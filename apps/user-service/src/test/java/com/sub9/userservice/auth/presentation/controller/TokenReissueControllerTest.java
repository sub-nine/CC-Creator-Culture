package com.sub9.userservice.auth.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.userservice.auth.application.service.TokenReissueService;
import com.sub9.userservice.auth.domain.exception.AuthErrorCode;
import com.sub9.userservice.auth.presentation.response.TokenReissueResponse;
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
        controllers = TokenReissueController.class,
        properties = "auth.jwt.secret=test-secret")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Access Token 재발급 API")
class TokenReissueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenReissueService tokenReissueService;

    @Test
    @DisplayName("인증 없이 재발급에 성공하면 새 Access Token과 200을 반환한다")
    void returns_reissued_access_token() throws Exception {
        when(tokenReissueService.reissue(any()))
                .thenReturn(new TokenReissueResponse("new-access-token", "Bearer", 1800));

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Access Token이 재발급되었습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token은 AUTH_0002와 401을 반환한다")
    void returns_unauthorized_for_invalid_refresh_token() throws Exception {
        when(tokenReissueService.reissue(any()))
                .thenThrow(new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_0002"));
    }

    @Test
    @DisplayName("Refresh Token이 비어 있으면 400을 반환한다")
    void returns_bad_request_for_blank_refresh_token() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }

    @Test
    @DisplayName("Redis 장애는 COMMON_0009와 503을 반환한다")
    void returns_service_unavailable_for_redis_failure() throws Exception {
        when(tokenReissueService.reissue(any()))
                .thenThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0009"));
    }

    private String validRequest() {
        return """
                {"refreshToken": "refresh-token"}
                """;
    }
}
