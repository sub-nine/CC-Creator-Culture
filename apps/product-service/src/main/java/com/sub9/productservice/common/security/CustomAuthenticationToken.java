package com.sub9.productservice.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

public class CustomAuthenticationToken extends AbstractAuthenticationToken {
  private final AuthUser principal;

  public CustomAuthenticationToken(AuthUser principal) {
    super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));

    this.principal = principal;

    setAuthenticated(true);
  }

  public static CustomAuthenticationToken of(UUID userId, String role) {
    return new CustomAuthenticationToken(new AuthUser(userId, role));
  }

  public UUID getId() {
    return principal.id();
  }

  public String getRole() {
    return principal.role();
  }

  @Override
  public Object getPrincipal() {
    return principal;
  }

  @Override
  public Object getCredentials() {
    return null;
  }
}
