package com.sub9.userservice.config;

import com.sub9.common.security.CustomAccessDeniedHandler;
import com.sub9.common.security.CustomAuthenticationEntryPoint;
import com.sub9.userservice.auth.infrastructure.security.GatewayHeaderAuthenticationFilter;
import org.springframework.http.HttpMethod;
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
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
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
                // Servlet 전역 필터로 중복 등록되지 않도록 API 보안 체인 안에서만 생성합니다.
                new GatewayHeaderAuthenticationFilter(authenticationEntryPoint),
                UsernamePasswordAuthenticationFilter.class);
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PUBLIC_AUTH_PATHS)
                .permitAll()
                .requestMatchers("/api/v1/admin/managers")
                .hasRole("MASTER")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/creators/*/approval")
                .hasAnyRole("MASTER", "MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/v1/follows/*")
                .hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/follows/*")
                .hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/v1/follows", "/api/v1/follows/*")
                .hasRole("CUSTOMER")
                .anyRequest()
                .authenticated());

        return http.build();
    }
}
