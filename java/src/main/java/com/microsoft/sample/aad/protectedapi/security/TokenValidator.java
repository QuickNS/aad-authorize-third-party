package com.microsoft.sample.aad.protectedapi.security;


import com.microsoft.azure.spring.autoconfigure.aad.UserPrincipal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import lombok.NonNull;
import net.minidev.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class TokenValidator {

  private static final String CUSTOM_HEADER = "X-Secondary-Token";
  
  private final ActiveDirectoryConfig adConfig;

  @Autowired
  public TokenValidator(ActiveDirectoryConfig aadConfig) {
    this.adConfig = aadConfig;
  }

  /**
   * Performs basic validation of the authorization token (audience, expirancy dates and token
   * integrity).
   *
   * @return AuthContext object
   * @exception AccessDeniedException if the token is considered invalid
   */
  public AuthContext validateToken() throws AccessDeniedException {
    return validateTokenClaims(null, null);
  }

  /**
   * Performs basic validation of the authorization token including the existence of a role.
   *
   * @param requiredRole - a String containing a role that needs to be included in the authorization
   *     token
   * @return AuthContext object
   * @exception AccessDeniedException if the token is considered invalid
   */
  public AuthContext validateToken(@NonNull String requiredRole)
      throws AccessDeniedException {
    return validateTokenClaims(null, requiredRole);
  }

  /**
   * Performs basic validation of the authorization token and checks that therequester ID matches
   * the expected clientId.
   *
   * @param clientId - a String containing the clientId that must match the requester application
   *     clientId
   * @return AuthContext object
   * @exception AccessDeniedException if the token is considered invalid
   */
  public AuthContext validateTokenForClient(@NonNull String clientId)
      throws AccessDeniedException {
    return validateTokenClaims(clientId, null);
  }

  /**
   * Performs basic validation of the authorization token including the existence of a role and
   * checks that the requester ID matches the expected clientId.
   *
   * @param clientId - a String containing the clientId that must match the requester application
   *     clientId
   * @param requiredRole - a String containing a role that needs to be included in the authorization
   *     token
   * @return AuthContext object
   * @exception AccessDeniedException if the token is considered invalid
   */
  public AuthContext validateTokenForClient(
      @NonNull String clientId, @NonNull String requiredRole) throws AccessDeniedException {
    return validateTokenClaims(clientId, requiredRole);
  }

  private AuthContext validateTokenClaims(String clientId, String requiredRole)
      throws AccessDeniedException {

    // initialize AuthContext
    AuthContext ctx = new AuthContext();
    ctx.setRequiredRole(requiredRole);
    ctx.setRequestedClientId(clientId);

    // check authentication type is correct
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof UserPrincipal)) {
      throw new AccessDeniedException("Missing or invalid Authorization token");
    }

    // validate audience
    // this should be done automatically by AADAppRoleStatelessAuthenticationFilter
    UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
    List<?> audiences = (List<?>) principal.getClaim("aud");
    String audience = audiences.get(0).toString();
    if (!audience.equals(adConfig.getAppIdUri())) {
      throw new AccessDeniedException("Incorrect audience on Authorization token");
    }
    ctx.setAudience(audience);

    // check roles
    // this can be done automatically by specifying a hasRole restriction
    // in the @PreAuthorize annotation
    Set<String> roles =
        Optional.of(principal)
            .map(p -> p.getClaim("roles"))
            .map(r -> (JSONArray) r)
            .map(Collection::stream)
            .orElseGet(Stream::empty)
            .map(Object::toString)
            .collect(Collectors.toSet());
    if (requiredRole != null && !roles.contains(requiredRole)) {
      throw new AccessDeniedException(
          String.format("Authorization token is missing required role %s", requiredRole));
    }
    ctx.setRoles(new ArrayList<String>(roles));

    // get appId from claims
    String appId = (String) principal.getClaim("appid");
    ctx.setRequesterId(appId);
    
    // set clientId and tenantId from the addConfig
    ctx.setTenantId(this.adConfig.getTenantId());
    ctx.setApiClientId(this.adConfig.getClientId());

    if (clientId != null && !appId.equals(clientId)) {
      // doesn't match - so this is either an invalid call or an aggregator
      // check if the additional custom header was supplied
      // get request header
      HttpServletRequest request =
          ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

      String headerValue = request.getHeader(CUSTOM_HEADER);

      if (headerValue != null) {
        // header was supplied so we need to validate it
        ctx.setIsThirdPartyAccessAttempt(true);

        // decode secondary token
        NimbusJwtDecoder jwtDecoder =
            (NimbusJwtDecoder) JwtDecoders.fromOidcIssuerLocation(adConfig.getIssuer());
        Jwt secondaryToken = null;
        try {
          // decode token - this handles integrity checks and time validation
          secondaryToken = jwtDecoder.decode(headerValue);
        } catch (JwtValidationException exception) {
          throw new AccessDeniedException(
              String.format("%s is invalid: %s", CUSTOM_HEADER, exception.getMessage()));
        }

        String authorizedClientId = secondaryToken.getAudience().get(0);
        if (!authorizedClientId.equals("api://" + clientId)) {
          // supplied token authorizes access to a clientId different
          // from the clientId requested in the REST call
          throw new AccessDeniedException(
              String.format("Invalid audience claim on %s", CUSTOM_HEADER));
        }

        // check roles
        List<String> clientRoles = secondaryToken.getClaimAsStringList("roles");
        ctx.setClientRoles(clientRoles);

        Boolean hasClientRole = false;
        for (String role : clientRoles) {
          if (role.endsWith(requiredRole)) {
            hasClientRole = true;
            break;
          }
        }
        if (!hasClientRole) {
          throw new AccessDeniedException(
              String.format("%s token is missing required role %s", CUSTOM_HEADER, requiredRole));
        }
      } else {
        // no custom header supplied
        throw new AccessDeniedException(
            "Provided Authorization token does not grant " + "access to this resource");
      }
    } else {
      // appId matches clientId
      ctx.setIsThirdPartyAccessAttempt(false);
    }

    return ctx;
  }
}
