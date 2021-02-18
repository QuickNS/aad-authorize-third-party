using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Primitives;
using System.Diagnostics;
using System.IdentityModel.Tokens.Jwt;
using System.Linq;
using Microsoft.Identity.Web.Resource;
using System;
using System.Security.Authentication;
using Microsoft.Extensions.Options;

namespace dotnet.Security
{
    public static class TokenValidator
    {
        private static readonly string CUSTOM_HEADER = "X-Secondary-Token";

        public static AuthContext DoTokenValidationsForClientId(AzureADConfig azureAdConfig, HttpContext ctx, string clientId, string requiredRole)
        {
            // initialize AuthContext
            AuthContext authContext = new AuthContext();
            authContext.RequiredRole = requiredRole;
            authContext.RequestedClientId = clientId;
            authContext.Authorized = false; // we default to rejecting

            //validate audience
            string audience = ctx.User.Claims.FirstOrDefault(c => c.Type == "aud")?.Value;
            if (String.IsNullOrEmpty(audience)) {
                authContext.ForbidReason = $"Authorization token is invalid";
                return authContext;
            }
            authContext.Audience = audience;

            var expectedAudience = azureAdConfig.AppIdUri;
            if (audience != expectedAudience)
            {
                authContext.ForbidReason = $"Audience was {audience} - expected {expectedAudience}";
                return authContext;
            }

            // check authorization token has access to this API method
            if (!ctx.User.IsInRole(requiredRole))
            {
                authContext.ForbidReason = $"Authorization token is missing required role {requiredRole}";
                return authContext;
            }

            //roles are valid but let's get a reference to them anyway
            string[] roles = ctx.User.Claims.Where(c => c.Type == "http://schemas.microsoft.com/ws/2008/06/identity/claims/role").Select(c => c.Value).ToArray();
            authContext.Roles = roles.ToList();

            // get appId from claims
            string appId = ctx.User.Claims.FirstOrDefault(c => c.Type == "appid").Value;
            authContext.RequesterId = appId;

            // set clientId and tenantId from the addConfig
            var tenantId = azureAdConfig.TenantId;
            var apiClientId = azureAdConfig.ClientId;
            authContext.TenantId = tenantId;
            authContext.ApiClientId = apiClientId;

            // check if it's the brand making the request
            if (appId != clientId)
            {
                // doesn't match - so this is either an invalid call or an aggregator
                // check if the additional custom header was supplied
                // get request header
                StringValues resourceHeaderValue = StringValues.Empty;
                var headerExists = ctx.Request.Headers.TryGetValue(CUSTOM_HEADER, out resourceHeaderValue);
                if (headerExists)
                {
                    authContext.IsThirdPartyAccessAttempt = true;
                    // grab secondary token 
                    var handler = new JwtSecurityTokenHandler();
                    JwtSecurityToken secondaryToken = null;
                    try
                    {
                        secondaryToken = handler.ReadJwtToken(resourceHeaderValue);
                    }
                    catch (Exception ex)
                    {
                        authContext.ForbidReason = $"Error decoding secondary token - {ex.Message}";
                        return authContext;
                    }

                    var targetClientId = secondaryToken.Audiences.FirstOrDefault();
                    if (targetClientId.StartsWith("api://"))
                    {
                        targetClientId = targetClientId.Remove(0, 6);
                    }

                    // let's validate if this is the right clientId
                    if (targetClientId != clientId)
                    {
                        // the secondary token is for another client
                        authContext.ForbidReason = $"Secondary Token is for {targetClientId} - expected {clientId}";
                        return authContext;
                    }

                    // verify brand roles
                    string[] clientRoles = secondaryToken.Claims.FirstOrDefault(c => c.Type == "roles").Value.Split(",");
                    authContext.ClientRoles = clientRoles.ToList();

                    bool hasClientRole = false;
                    foreach (string role in clientRoles)
                    {
                        if (role.EndsWith(requiredRole))
                        {
                            hasClientRole = true;
                            break;
                        }
                    }
                    if (!hasClientRole)
                    {
                        // the secondary token is for another client
                        authContext.ForbidReason = $"{CUSTOM_HEADER} is missing {requiredRole}";
                        return authContext;
                    }

                    //all required checks passed
                    authContext.Authorized = true;
                }
                else //no custom header supplied
                {
                    authContext.ForbidReason = $"Provided Authorization token does not grant access to this resource";
                    return authContext;
                }
            }
            else
            {
                // appId matches clientId
                authContext.IsThirdPartyAccessAttempt = false;
                authContext.Authorized = true;
            }

            return authContext;
        }
    }
}


