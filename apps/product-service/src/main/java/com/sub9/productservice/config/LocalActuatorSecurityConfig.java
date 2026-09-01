package com.sub9.productservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalActuatorSecurityConfig {

    @Bean
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/prometheus"
                )
                .permitAll()
                .anyRequest()
                .authenticated()
        );

        return http.build();
    }
}
