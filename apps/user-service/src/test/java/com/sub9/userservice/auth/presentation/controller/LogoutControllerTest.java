package com.sub9.userservice.auth.presentation.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.userservice.auth.application.service.LogoutService;
import com.sub9.userservice.auth.domain.exception.AuthenticationTokenStorageException;
import com.sub9.userservice.config.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(
        controllers = LogoutController.class,
        properties = "auth.jwt.secret=test-secret")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("로그아웃 API")
class LogoutControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private static final UUID ACCESS_TOKEN_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000002");
    private static final long EXPIRES_AT = 1_788_400_000L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogoutService logoutService;

    @Test
    @DisplayName("인증된 내부 헤더로 로그아웃하면 200을 반환한다")
    void logs_out_with_authenticated_internal_headers() throws Exception {
        mockMvc.perform(logoutRequest().with(user("gateway")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃이 완료되었습니다."))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(logoutService).logout(USER_ID, ACCESS_TOKEN_ID, EXPIRES_AT);
    }

    @Test
    @DisplayName("현재 Security 기본 정책은 비인증 로그아웃을 403으로 거부한다")
    void rejects_unauthenticated_logout() throws Exception {
        mockMvc.perform(logoutRequest().with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("필수 내부 헤더가 없으면 400을 반환한다")
    void rejects_logout_without_required_internal_header() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "CUSTOMER")
                        .header("X-Token-Id", ACCESS_TOKEN_ID)
                        .with(user("gateway"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Redis Lua Script 장애는 COMMON_0009와 503을 반환한다")
    void returns_service_unavailable_for_redis_failure() throws Exception {
        doThrow(new AuthenticationTokenStorageException())
                .when(logoutService)
                .logout(USER_ID, ACCESS_TOKEN_ID, EXPIRES_AT);

        mockMvc.perform(logoutRequest().with(user("gateway")).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.SERVICE_UNAVAILABLE.code()));
    }

    private MockHttpServletRequestBuilder logoutRequest() {
        return post("/api/v1/auth/logout")
                .header("X-User-Id", USER_ID)
                .header("X-User-Role", "CUSTOMER")
                .header("X-Token-Id", ACCESS_TOKEN_ID)
                .header("X-Token-Expires-At", EXPIRES_AT);
    }
}
