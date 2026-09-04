package com.sub9.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Security 오류 응답 처리기")
class SecurityErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("인증 실패는 COMMON_0007과 401을 반환한다")
    void writes_unauthorized_response() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CustomAuthenticationEntryPoint(objectMapper).commence(
                new MockHttpServletRequest(), response, new BadCredentialsException("invalid"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("COMMON_0007", "인증이 필요합니다.");
    }

    @Test
    @DisplayName("인가 실패는 COMMON_0008과 403을 반환한다")
    void writes_forbidden_response() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CustomAccessDeniedHandler(objectMapper).handle(
                new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("COMMON_0008", "접근 권한이 없습니다.");
    }
}
