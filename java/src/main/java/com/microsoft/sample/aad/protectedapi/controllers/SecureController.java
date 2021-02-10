package com.microsoft.sample.aad.protectedapi.controllers;

import com.microsoft.sample.aad.protectedapi.security.AuthContext;
import com.microsoft.sample.aad.protectedapi.security.TokenValidator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SecureController {

    // this is only needed for the debug method
    @Autowired
    private TokenValidator tokenValidator;
    
    @GetMapping("/{clientId}/data")
    @PreAuthorize("hasPermission(#clientId, 'ClientId', 'API.Read')")
    public String ReadData(@PathVariable String clientId) {
        return String.format("Request Validated! - %s", clientId);
    }

    @PutMapping("/{clientId}/data")
    @PreAuthorize("hasPermission(#clientId, 'ClientId', 'API.Write')")
    public String WriteData(@PathVariable String clientId) {
        return String.format("Request Validated! - %s", clientId);
    }

    @GetMapping("/{clientId}/debug")
    public AuthContext Debug(@PathVariable String clientId) {
        // rather than using the PreAuthorize method, we are explicitly calling
        // the tokenValidator's validateTokenForClient method which performs the same operations
        // but allowing us to analyze the AuthContext object created during token validation
        AuthContext authContext = tokenValidator.validateTokenForClient(clientId, "API.Read");
        return authContext;
    }
}
