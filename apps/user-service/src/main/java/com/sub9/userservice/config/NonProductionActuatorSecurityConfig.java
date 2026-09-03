package com.sub9.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
public class NonProductionActuatorSecurityConfig {

    private static final String[] PUBLIC_SIGNUP_PATHS = {
            "/api/v1/auth/signup/customer",
            "/api/v1/auth/signup/creator"
    };

    @Bean
    @Order(1)
    SecurityFilterChain nonProductionSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator/**");
        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/prometheus"
                )
                .permitAll()
                .requestMatchers(PUBLIC_SIGNUP_PATHS)
                .permitAll()
                .anyRequest()
                .authenticated());

        return http.build();
    }
}
