package com.sub9.userservice.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.common.security.CustomAuthenticationEntryPoint;
import com.sub9.userservice.user.domain.model.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Gateway 내부 헤더 인증 필터")
class GatewayHeaderAuthenticationFilterTest {

    private static final UUID USER_ID = UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private static final UUID TOKEN_ID = UUID.fromString("0198f2a0-76c0-7000-8000-000000000002");

    private final GatewayHeaderAuthenticationFilter filter = new GatewayHeaderAuthenticationFilter(
            new CustomAuthenticationEntryPoint(new ObjectMapper()));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("네 내부 헤더를 인증 객체로 변환한다")
    void authenticates_gateway_headers() throws Exception {
        MockHttpServletRequest request = protectedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_CUSTOMER");
        assertThat(authentication.getPrincipal())
                .isEqualTo(new GatewayAuthenticationPrincipal(USER_ID, UserRole.CUSTOMER, TOKEN_ID, 1_788_400_000L));
    }

    @Test
    @DisplayName("내부 헤더가 누락되면 COMMON_0007과 401을 반환한다")
    void rejects_missing_header() throws Exception {
        MockHttpServletRequest request = protectedRequest();
        request.removeHeader(GatewayHeaderAuthenticationFilter.TOKEN_ID_HEADER);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("COMMON_0007");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("내부 헤더 형식이 잘못되면 COMMON_0007과 401을 반환한다")
    void rejects_malformed_header() throws Exception {
        MockHttpServletRequest request = protectedRequest();
        request.removeHeader(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER);
        request.addHeader(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, "INVALID");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("COMMON_0007");
    }

    @Test
    @DisplayName("로그인 공개 경로는 내부 헤더 검사를 생략한다")
    void skips_public_login_path() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest protectedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.setServletPath("/api/v1/auth/logout");
        request.addHeader(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, USER_ID.toString());
        request.addHeader(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, "CUSTOMER");
        request.addHeader(GatewayHeaderAuthenticationFilter.TOKEN_ID_HEADER, TOKEN_ID.toString());
        request.addHeader(GatewayHeaderAuthenticationFilter.TOKEN_EXPIRES_AT_HEADER, "1788400000");
        return request;
    }
}
