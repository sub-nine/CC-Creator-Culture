package com.sub9.userservice.auth.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.userservice.auth.application.service.SignupService;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.auth.presentation.request.CustomerSignupRequest;
import com.sub9.userservice.auth.presentation.response.CreatorSignupResponse;
import com.sub9.userservice.auth.presentation.response.CustomerSignupResponse;
import com.sub9.userservice.config.SecurityConfig;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.user.domain.model.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("회원가입 API")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignupService signupService;

    @Test
    @DisplayName("인증 없이 CUSTOMER 회원가입에 성공하면 201을 반환한다")
    void when_valid_customer_request_is_sent_then_created_response_is_returned() throws Exception {
        UUID userId = UUID.fromString("01990a00-0000-7000-8000-000000000001");
        when(signupService.signupCustomer(any())).thenReturn(
                new CustomerSignupResponse(userId, "user@example.com", "user", UserRole.CUSTOMER));

        mockMvc.perform(post("/api/v1/auth/signup/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password123!",
                                  "nickname": "user",
                                  "phone": "010-1234-5678",
                                  "address": "서울시 예시구",
                                  "slackId": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("인증 없이 CREATOR 회원가입에 성공하면 PENDING 응답과 201을 반환한다")
    void when_valid_creator_request_is_sent_then_pending_creator_response_is_returned()
            throws Exception {
        UUID userId = UUID.fromString("01990a00-0000-7000-8000-000000000002");
        UUID creatorId = UUID.fromString("01990a00-0000-7000-8000-000000000003");
        when(signupService.signupCreator(any())).thenReturn(new CreatorSignupResponse(
                userId,
                "creator@example.com",
                "creator",
                UserRole.CREATOR,
                creatorId,
                ApprovalStatus.PENDING));

        mockMvc.perform(post("/api/v1/auth/signup/creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatorRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.creatorId").value(creatorId.toString()))
                .andExpect(jsonPath("$.data.role").value("CREATOR"))
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("비밀번호 조합이 잘못되면 400을 반환한다")
    void when_password_complexity_is_invalid_then_bad_request_is_returned() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password",
                                  "nickname": "user",
                                  "phone": "010-1234-5678",
                                  "address": "서울시 예시구"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }

    @Test
    @DisplayName("잘못된 이메일과 전화번호는 400을 반환한다")
    void when_email_and_phone_formats_are_invalid_then_bad_request_is_returned() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": "Password123!",
                                  "nickname": "user",
                                  "phone": "010-ABCD-5678",
                                  "address": "서울시 예시구"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"))
                .andExpect(jsonPath("$.errors[*].email").exists())
                .andExpect(jsonPath("$.errors[*].phone").exists());
    }

    @Test
    @DisplayName("잘못된 사업자등록번호는 400을 반환한다")
    void when_business_number_format_is_invalid_then_bad_request_is_returned() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatorRequest().replace("123-45-67890", "123-AB-67890")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"))
                .andExpect(jsonPath("$.errors[*].businessRegistrationNumber").exists());
    }

    @Test
    @DisplayName("필수 가입 정보가 비어 있으면 400을 반환한다")
    void when_required_signup_values_are_blank_then_bad_request_is_returned() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " ",
                                  "password": "Password123!",
                                  "nickname": " ",
                                  "phone": " ",
                                  "address": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }

    @Test
    @DisplayName("회원가입 중복 오류는 도메인 오류 코드와 409를 반환한다")
    void when_signup_value_is_duplicated_then_conflict_response_is_returned() throws Exception {
        when(signupService.signupCustomer(any(CustomerSignupRequest.class)))
                .thenThrow(new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS));

        mockMvc.perform(post("/api/v1/auth/signup/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password123!",
                                  "nickname": "user",
                                  "phone": "010-1234-5678",
                                  "address": "서울시 예시구"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_0001"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    private String validCreatorRequest() {
        return """
                {
                  "email": "creator@example.com",
                  "password": "Password123!",
                  "nickname": "creator",
                  "phone": "010-9876-5432",
                  "address": "서울시 예시구",
                  "slackId": null,
                  "creatorName": "창작상점",
                  "businessRegistrationNumber": "123-45-67890"
                }
                """;
    }
}
