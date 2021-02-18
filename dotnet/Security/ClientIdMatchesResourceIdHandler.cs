using System;
using System.Diagnostics;
using System.Text;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc.Filters;
using Microsoft.Extensions.Options;
using Newtonsoft.Json.Linq;

namespace dotnet.Security
{
    public class ClientIdMatchesResourceIdHandler : AuthorizationHandler<ResourcePermissionRequirement>
    {
        // required to access HTTP Context in a custom component
        private readonly IHttpContextAccessor _httpContextAccessor;
        private AzureADConfig _azureADConfig;
        
        // HttpContextAccessor is initialized through dependency injection
        public ClientIdMatchesResourceIdHandler(IHttpContextAccessor httpContextAccessor, IOptions<AzureADConfig> azureAdConfig)
        {
            _httpContextAccessor = httpContextAccessor ?? throw new ArgumentNullException(nameof(httpContextAccessor));
            _azureADConfig = azureAdConfig.Value;
        }

        protected override Task HandleRequirementAsync(AuthorizationHandlerContext context, ResourcePermissionRequirement requirement)
        {
            var httpContext = _httpContextAccessor.HttpContext;
            object clientIdTemp = null;
            if (!httpContext.Request.RouteValues.TryGetValue("clientId", out clientIdTemp)) {
                context.Fail();
                return Task.CompletedTask;
            }
            string clientId = clientIdTemp.ToString();
            var authContext = TokenValidator.DoTokenValidationsForClientId(_azureADConfig, httpContext, clientId, requirement.RequiredRole);
            if (authContext.Authorized)
            {
                context.Succeed(requirement);
            }
            else
            {
                string response = JObject.FromObject(authContext).ToString();
                //byte[] bytes = Encoding.UTF8.GetBytes("{ \"Unauthorized\": \"" + authContext.ForbidReason + "\"}");
                byte[] bytes = Encoding.UTF8.GetBytes(response);
                httpContext.Response.StatusCode = 403;
                httpContext.Response.ContentType = "application/json";
                httpContext.Response.Body.WriteAsync(bytes, 0, bytes.Length);
                httpContext.Response.Body.FlushAsync();
            }
            return Task.CompletedTask;
        }
    }
}