package com.sub9.userservice.auth.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.userservice.auth.application.service.ManagerAccountService;
import com.sub9.userservice.auth.presentation.request.CreateManagerRequest;
import com.sub9.userservice.auth.presentation.response.ManagerAccountResponse;
import com.sub9.userservice.config.SecurityConfig;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(AdminManagerController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("관리자 MANAGER 계정 생성 API")
class AdminManagerControllerTest {

    private static final UUID MASTER_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID TOKEN_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagerAccountService managerAccountService;

    @Test
    @DisplayName("MASTER는 MANAGER 계정을 생성할 수 있다")
    void when_master_requests_manager_account_created_response_is_returned() throws Exception {
        UUID managerId = UUID.fromString("01990a00-0000-7000-8000-000000000003");
        when(managerAccountService.createManager(any(), any(CreateManagerRequest.class)))
                .thenReturn(new ManagerAccountResponse(
                        managerId, "manager@example.com", "manager", UserRole.MANAGER));

        mockMvc.perform(withGatewayHeaders(post("/api/v1/admin/managers"), "MASTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("MANAGER 계정이 생성되었습니다."))
                .andExpect(jsonPath("$.data.userId").value(managerId.toString()))
                .andExpect(jsonPath("$.data.role").value("MANAGER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("MASTER가 아닌 사용자는 MANAGER 계정을 생성할 수 없다")
    void when_non_master_requests_forbidden_response_is_returned() throws Exception {
        mockMvc.perform(withGatewayHeaders(post("/api/v1/admin/managers"), "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0008"));
    }

    @Test
    @DisplayName("인증 헤더가 없으면 MANAGER 계정을 생성할 수 없다")
    void when_authentication_headers_are_missing_unauthorized_response_is_returned()
            throws Exception {
        mockMvc.perform(post("/api/v1/admin/managers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0007"));
    }

    private MockHttpServletRequestBuilder withGatewayHeaders(
            MockHttpServletRequestBuilder request, String role) {
        return request
                .header("X-User-Id", MASTER_ID)
                .header("X-User-Role", role)
                .header("X-Token-Id", TOKEN_ID)
                .header("X-Token-Expires-At", "4102444800");
    }

    private String validRequest() {
        return """
                {
                  "email": "manager@example.com",
                  "password": "Password123!",
                  "nickname": "manager",
                  "phone": "010-1234-5678",
                  "address": "서울시 예시구",
                  "slackId": null
                }
                """;
    }
}
