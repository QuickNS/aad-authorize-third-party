package com.microsoft.sample.aad.protectedapi.security;


import javax.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "azure.activedirectory") // load values from config file
@Data
@Validated
public class ActiveDirectoryConfig {

  @NotEmpty
  private String clientId;

  @NotEmpty
  private String appIdUri;

  @NotEmpty
  private String tenantId;

  // add additional property for the Issuer (required for JWT decoding)
  public String getIssuer() {
    return String.format("https://sts.windows.net/%s/", this.tenantId);
  }
}
