package com.sub9.productservice.config;

import com.sub9.productservice.common.security.UserContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  @Order(2)
  public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
    // csrf 비활성화
    httpSecurity.csrf(AbstractHttpConfigurer::disable);

    // 세션 STATELESS
    httpSecurity.sessionManagement(
        sessionManagement ->
            sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    // 필터 관리
    httpSecurity.addFilterBefore(
        new UserContextFilter(), UsernamePasswordAuthenticationFilter.class);

    // URL 인가 설정
    httpSecurity.authorizeHttpRequests(
        requests ->
            requests
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/internal/**")
                .permitAll()
                // TODO : 임시로 모든 요청 허용
                .anyRequest()
                .permitAll());
    //                                .anyRequest().authenticated());

    return httpSecurity.build();
  }

  @Bean
  public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("ROLE_MASTER > ROLE_MANAGER");
  }
}
