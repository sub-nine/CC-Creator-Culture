package com.sub9.productservice.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class UserContextFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String userIdStr = request.getHeader("X-User-Id");
    String roleStr = request.getHeader("X-Role");

    if (StringUtils.hasText(userIdStr)
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      UUID userId = UUID.fromString(userIdStr);
      AuthUser authUser = new AuthUser(userId, roleStr);

      CustomAuthenticationToken authenticationToken = new CustomAuthenticationToken(authUser);
      SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }
    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/internal/");
  }
}
