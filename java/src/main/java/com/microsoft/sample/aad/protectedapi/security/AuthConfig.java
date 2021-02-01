package com.microsoft.sample.aad.protectedapi.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "authentication") // load values from config file
public class AuthConfig {

  private Boolean anonymousAccess;

  /**
   * Returns the value of authentication.anonymous-access setting on the application.yml file
   */
  public Boolean getAnonymousAccess() {
    if (anonymousAccess == null) {
      return false;
    } else {
      return anonymousAccess;
    }
  }
}
