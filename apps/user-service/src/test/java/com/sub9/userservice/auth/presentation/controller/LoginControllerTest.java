package com.sub9.userservice.auth.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.userservice.auth.application.service.LoginService;
import com.sub9.userservice.auth.domain.exception.AuthErrorCode;
import com.sub9.userservice.auth.presentation.response.LoginResponse;
import com.sub9.userservice.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = LoginController.class,
        properties = "auth.jwt.secret=test-secret")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("로그인 API")
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    @Test
    @DisplayName("인증 없이 로그인에 성공하면 토큰과 200을 반환한다")
    void when_valid_credentials_are_sent_then_token_response_is_returned() throws Exception {
        when(loginService.login(any())).thenReturn(
                new LoginResponse("access-token", "refresh-token", "Bearer", 1800));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " User@Example.COM ",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그인이 완료되었습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800));
    }

    @Test
    @DisplayName("잘못된 자격 증명은 AUTH_0001과 401을 반환한다")
    void when_credentials_are_invalid_then_unauthorized_is_returned() throws Exception {
        when(loginService.login(any()))
                .thenThrow(new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_0001"));
    }

    @Test
    @DisplayName("승인 대기 CREATOR는 AUTH_0003과 403을 반환한다")
    void when_creator_is_pending_then_forbidden_is_returned() throws Exception {
        when(loginService.login(any()))
                .thenThrow(new BusinessException(AuthErrorCode.CREATOR_APPROVAL_PENDING));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_0003"));
    }

    @Test
    @DisplayName("로그인 필수값이 비어 있으면 400을 반환한다")
    void when_required_login_values_are_blank_then_bad_request_is_returned() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " ",
                                  "password": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }

    private String validLoginRequest() {
        return """
                {
                  "email": "creator@example.com",
                  "password": "Password123!"
                }
                """;
    }
}
