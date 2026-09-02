package com.sub9.productservice.config;

import com.sub9.productservice.common.security.CustomAuthenticationToken;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

@Configuration
public class CustomAuditorAware implements AuditorAware<UUID> {

  @Override
  public Optional<UUID> getCurrentAuditor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof CustomAuthenticationToken token && token.isAuthenticated()) {
      return Optional.ofNullable(token.getId());
    }

    return Optional.empty();
  }
}
