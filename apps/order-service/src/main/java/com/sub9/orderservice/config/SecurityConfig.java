package com.sub9.orderservice.config;

import com.sub9.common.dto.response.ErrorResponse;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import com.sub9.orderservice.common.security.GatewayHeaderAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    AuthenticationEntryPoint orderAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) ->
                writeErrorResponse(response, objectMapper, CommonErrorCode.UNAUTHORIZED);
    }

    @Bean
    AccessDeniedHandler orderAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) ->
                writeErrorResponse(response, objectMapper, CommonErrorCode.FORBIDDEN);
    }

    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint orderAuthenticationEntryPoint,
            AccessDeniedHandler orderAccessDeniedHandler) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint(orderAuthenticationEntryPoint)
                .accessDeniedHandler(orderAccessDeniedHandler));
        http.addFilterBefore(
                new GatewayHeaderAuthenticationFilter(orderAuthenticationEntryPoint),
                UsernamePasswordAuthenticationFilter.class);
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/v1/orders")
                .hasRole(GatewayAuthenticationPrincipal.Role.CUSTOMER.name())
                .anyRequest()
                .authenticated());

        return http.build();
    }

    private static void writeErrorResponse(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            CommonErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.from(errorCode));
    }
}
