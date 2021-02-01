package com.microsoft.sample.aad.protectedapi.security;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AuthContext {
  private String audience;
  private String requesterId;
  private String requiredRole;
  private Boolean isThirdPartyAccessAttempt;
  private List<String> roles;
  private String requestedClientId;
  private List<String> clientRoles;
  private String apiClientId;
  private String tenantId;
}
