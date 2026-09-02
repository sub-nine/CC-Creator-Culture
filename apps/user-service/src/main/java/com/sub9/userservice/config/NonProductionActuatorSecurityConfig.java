package com.sub9.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
public class NonProductionActuatorSecurityConfig {

    private static final String[] PUBLIC_SIGNUP_PATHS = {
            "/api/v1/auth/signup/customer",
            "/api/v1/auth/signup/creator"
    };

    @Bean
    SecurityFilterChain nonProductionSecurityFilterChain(HttpSecurity http) throws Exception {

        http.csrf(csrf -> csrf.ignoringRequestMatchers(PUBLIC_SIGNUP_PATHS));

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
                .authenticated()
        );

        return http.build();
    }
}
