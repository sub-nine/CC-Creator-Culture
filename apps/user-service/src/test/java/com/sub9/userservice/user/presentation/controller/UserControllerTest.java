package com.sub9.userservice.user.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.auth.domain.exception.AuthenticationTokenStorageException;
import com.sub9.userservice.config.SecurityConfig;
import com.sub9.userservice.user.application.service.UserDeletionService;
import com.sub9.userservice.user.application.service.UserProfileService;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.presentation.request.UpdateMyProfileRequest;
import com.sub9.userservice.user.presentation.response.MyProfileResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
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

    @MockitoBean
    private UserDeletionService userDeletionService;

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

    @Test
    @DisplayName("인증된 사용자는 전달한 내 정보를 수정할 수 있다")
    void when_authenticated_user_updates_profile_updated_profile_is_returned() throws Exception {
        when(userProfileService.updateMyProfile(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.any(UpdateMyProfileRequest.class)))
                .thenReturn(new MyProfileResponse(
                        USER_ID, "user@example.com", "변경된 사용자", "01099998888",
                        "새 주소", null, UserRole.CUSTOMER));

        mockMvc.perform(withGatewayHeaders(patch("/api/v1/users/me"), UserRole.CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "  변경된 사용자  ",
                                  "phone": "010-9999-8888",
                                  "address": " 새 주소 ",
                                  "slackId": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 정보를 수정했습니다."))
                .andExpect(jsonPath("$.data.nickname").value("변경된 사용자"))
                .andExpect(jsonPath("$.data.phone").value("01099998888"))
                .andExpect(jsonPath("$.data.address").value("새 주소"))
                .andExpect(jsonPath("$.data.slackId").doesNotExist());

        ArgumentCaptor<UpdateMyProfileRequest> requestCaptor =
                ArgumentCaptor.forClass(UpdateMyProfileRequest.class);
        verify(userProfileService).updateMyProfile(
                org.mockito.ArgumentMatchers.eq(USER_ID), requestCaptor.capture());
        assertThat(requestCaptor.getValue().nickname())
                .isEqualTo("변경된 사용자");
        assertThat(requestCaptor.getValue().phone())
                .isEqualTo("01099998888");
        assertThat(requestCaptor.getValue().slackIdProvided())
                .isTrue();
    }

    @Test
    @DisplayName("수정할 필드가 없으면 400을 반환한다")
    void when_update_request_is_empty_update_my_profile_returns_bad_request() throws Exception {
        mockMvc.perform(withGatewayHeaders(patch("/api/v1/users/me"), UserRole.CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }

    @Test
    @DisplayName("필수 프로필 값을 null로 수정하면 400을 반환한다")
    void when_required_profile_value_is_null_update_my_profile_returns_bad_request()
            throws Exception {
        mockMvc.perform(withGatewayHeaders(patch("/api/v1/users/me"), UserRole.CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }

    @Test
    @DisplayName("인증된 사용자가 회원 탈퇴하면 204를 반환한다")
    void when_authenticated_user_deletes_account_response_is_no_content() throws Exception {
        mockMvc.perform(withGatewayHeaders(delete("/api/v1/users/me"), UserRole.CUSTOMER))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEmpty());

        verify(userDeletionService).deleteMyAccount(USER_ID, TOKEN_ID, 4102444800L);
    }

    @Test
    @DisplayName("인증 정보 없이 회원 탈퇴하면 401을 반환한다")
    void when_authentication_is_missing_delete_my_account_returns_unauthorized()
            throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0007"));
    }

    @Test
    @DisplayName("탈퇴할 수 없는 역할이면 403을 반환한다")
    void when_role_cannot_delete_account_response_is_forbidden() throws Exception {
        doThrow(new AccessDeniedException("forbidden"))
                .when(userDeletionService)
                .deleteMyAccount(USER_ID, TOKEN_ID, 4102444800L);

        mockMvc.perform(withGatewayHeaders(delete("/api/v1/users/me"), UserRole.MASTER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0008"));
    }

    @Test
    @DisplayName("탈퇴 대상 사용자가 없으면 404를 반환한다")
    void when_active_user_does_not_exist_delete_my_account_returns_not_found()
            throws Exception {
        doThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .when(userDeletionService)
                .deleteMyAccount(USER_ID, TOKEN_ID, 4102444800L);

        mockMvc.perform(withGatewayHeaders(delete("/api/v1/users/me"), UserRole.CUSTOMER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_0007"));
    }

    @Test
    @DisplayName("토큰 무효화에 실패하면 503을 반환한다")
    void when_token_invalidation_fails_delete_my_account_returns_service_unavailable()
            throws Exception {
        doThrow(new AuthenticationTokenStorageException())
                .when(userDeletionService)
                .deleteMyAccount(USER_ID, TOKEN_ID, 4102444800L);

        mockMvc.perform(withGatewayHeaders(delete("/api/v1/users/me"), UserRole.CUSTOMER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0009"));
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
