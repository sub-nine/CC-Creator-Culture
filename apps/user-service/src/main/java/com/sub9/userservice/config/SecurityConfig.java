package com.sub9.userservice.config;

import com.sub9.common.security.CustomAccessDeniedHandler;
import com.sub9.common.security.CustomAuthenticationEntryPoint;
import com.sub9.userservice.auth.infrastructure.security.GatewayHeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_PATHS = {
            "/api/v1/auth/signup/customer",
            "/api/v1/auth/signup/creator",
            "/api/v1/auth/login",
            "/api/v1/auth/reissue"
    };

    @Bean
    CustomAuthenticationEntryPoint customAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new CustomAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    CustomAccessDeniedHandler customAccessDeniedHandler(ObjectMapper objectMapper) {
        return new CustomAccessDeniedHandler(objectMapper);
    }

    @Bean
    GatewayHeaderAuthenticationFilter gatewayHeaderAuthenticationFilter(
            CustomAuthenticationEntryPoint authenticationEntryPoint) {
        return new GatewayHeaderAuthenticationFilter(authenticationEntryPoint);
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GatewayHeaderAuthenticationFilter gatewayHeaderAuthenticationFilter,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        http.addFilterBefore(
                gatewayHeaderAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PUBLIC_AUTH_PATHS)
                .permitAll()
                .anyRequest()
                .authenticated());

        return http.build();
    }
}
