package com.sub9.userservice.user.presentation.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.config.SecurityConfig;
import com.sub9.userservice.user.application.service.UserProfileService;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.presentation.response.MyProfileResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("내 정보 조회 API")
class UserControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID TOKEN_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService userProfileService;

    @ParameterizedTest
    @EnumSource(UserRole.class)
    @DisplayName("인증된 사용자는 역할과 관계없이 내 정보를 조회할 수 있다")
    void when_authenticated_user_gets_my_profile_profile_is_returned(UserRole role)
            throws Exception {
        when(userProfileService.getMyProfile(USER_ID)).thenReturn(profile(role));

        mockMvc.perform(withGatewayHeaders(get("/api/v1/users/me"), role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("사용자"))
                .andExpect(jsonPath("$.data.phone").value("01012345678"))
                .andExpect(jsonPath("$.data.address").value("서울시 예시구"))
                .andExpect(jsonPath("$.data.slackId").doesNotExist())
                .andExpect(jsonPath("$.data.role").value(role.name()))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.createdBy").doesNotExist())
                .andExpect(jsonPath("$.data.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.data.deletedBy").doesNotExist());

        verify(userProfileService).getMyProfile(USER_ID);
    }

    @Test
    @DisplayName("인증 정보가 없으면 401을 반환한다")
    void when_authentication_is_missing_get_my_profile_returns_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0007"));
    }

    @Test
    @DisplayName("활성 사용자가 없으면 404를 반환한다")
    void when_active_user_does_not_exist_get_my_profile_returns_not_found() throws Exception {
        when(userProfileService.getMyProfile(USER_ID))
                .thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        mockMvc.perform(withGatewayHeaders(get("/api/v1/users/me"), UserRole.CUSTOMER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_0007"));
    }

    private MyProfileResponse profile(UserRole role) {
        return new MyProfileResponse(
                USER_ID, "user@example.com", "사용자", "01012345678",
                "서울시 예시구", null, role);
    }

    private MockHttpServletRequestBuilder withGatewayHeaders(
            MockHttpServletRequestBuilder request, UserRole role) {
        return request
                .header("X-User-Id", USER_ID)
                .header("X-User-Role", role.name())
                .header("X-Token-Id", TOKEN_ID)
                .header("X-Token-Expires-At", "4102444800");
    }
}
