package com.microsoft.sample.aad.protectedapi.security;

import java.io.Serializable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class TokenPermissionEvaluator implements PermissionEvaluator {

  @Autowired
  TokenValidator tokenValidator;

  @Override
  public boolean hasPermission(Authentication auth, Serializable targetId, String targetType,
      Object permission) {
    if ((auth == null) || !(permission instanceof String)) {
      return false;
    }
    if (targetType.compareTo("ClientId") != 0) {
      String message = String.format("This targetType (%s) is not supported by this PermissionEvaluator: %s", targetType, TokenPermissionEvaluator.class);
      UnsupportedOperationException exception = new UnsupportedOperationException(message);
      throw exception;
    }

    return hasPrivilege(targetId.toString(), permission.toString());
  }

  @Override
  public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
    String message =
        "Not supported by this PermissionEvaluator: " + TokenPermissionEvaluator.class;
    UnsupportedOperationException exception = new UnsupportedOperationException(message);
    throw exception;
  }

  private boolean hasPrivilege(String targetId, String role) {
    AuthContext context = tokenValidator.validateTokenForClient(targetId, role);
    return true;
  }
}
