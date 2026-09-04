package com.sub9.productservice.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
public class NonProductionActuatorSecurityConfig {

  @Bean
  @Order(1)
  SecurityFilterChain nonProductionSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/actuator/**")
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/actuator/health", "/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    .anyRequest()
                    .authenticated());

    return http.build();
  }
}
