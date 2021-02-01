package com.microsoft.sample.aad.protectedapi.security;

import java.io.IOException;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

public class CustomAccessDeniedHandler implements AccessDeniedHandler {

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException, ServletException {

    response.setContentType("application/json;charset=UTF-8");
    response.setStatus(403);

    response
        .getWriter()
        .write(
            new JSONObject()
                .put("timestamp", new Date().toString())
                .put("status", 403)
                .put("error", "Forbidden")
                .put("message", accessDeniedException.getMessage())
                .put("path", request.getPathInfo())
                .toString());
  }
}
