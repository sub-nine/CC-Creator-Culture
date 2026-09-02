package com.sub9.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("!local")
public class SecurityConfig {

    private static final String[] PUBLIC_SIGNUP_PATHS = {
            "/api/v1/auth/signup/customer",
            "/api/v1/auth/signup/creator"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers(PUBLIC_SIGNUP_PATHS));
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PUBLIC_SIGNUP_PATHS)
                .permitAll()
                .anyRequest()
                .authenticated()
        );

        return http.build();
    }
}
