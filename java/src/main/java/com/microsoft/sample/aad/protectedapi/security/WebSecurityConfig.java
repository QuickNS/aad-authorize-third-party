package com.microsoft.sample.aad.protectedapi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.azure.spring.autoconfigure.aad.AADAppRoleStatelessAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private AADAppRoleStatelessAuthenticationFilter aadAuthFilter;

    @Autowired AuthConfig authConfig;

    Logger logger = LoggerFactory.getLogger(WebSecurityConfig.class);

    @Bean
    protected CustomAccessDeniedHandler accessDeniedHandler() {
        return new CustomAccessDeniedHandler();
    };    

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable();

        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        if (authConfig.getAnonymousAccess())
        {
            http.authorizeRequests().anyRequest().permitAll(); 
            logger.info("Running with anonymous access enabled");
        }
        else {
            http.authorizeRequests().anyRequest().authenticated(); 
            logger.info("Running with anonymous access disabled");
        }
        
        // add override to default error handling to include more verbose error message on 403
        http.exceptionHandling().accessDeniedHandler(accessDeniedHandler());
        // add the AADAppRoleStatelessAuthenticationFilter to validate the JWT token
        http.addFilterBefore(aadAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
    }

   
}
