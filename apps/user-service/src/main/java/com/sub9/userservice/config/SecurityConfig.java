package com.sub9.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("!local")
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_PATHS = {
            "/api/v1/auth/signup/customer",
            "/api/v1/auth/signup/creator",
            "/api/v1/auth/login"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers(PUBLIC_AUTH_PATHS));
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PUBLIC_AUTH_PATHS)
                .permitAll()
                .anyRequest()
                .authenticated()
        );

        return http.build();
    }
}
