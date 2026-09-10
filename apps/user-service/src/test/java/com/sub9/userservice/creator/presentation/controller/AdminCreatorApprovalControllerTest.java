package com.sub9.userservice.creator.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.userservice.config.SecurityConfig;
import com.sub9.userservice.creator.application.service.CreatorApprovalService;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.presentation.request.CreatorApprovalRequest;
import com.sub9.userservice.creator.presentation.response.CreatorApprovalResponse;
import java.time.LocalDateTime;
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

@WebMvcTest(AdminCreatorApprovalController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("관리자 창작자 가입 심사 API")
class AdminCreatorApprovalControllerTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID TOKEN_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000002");
    private static final UUID CREATOR_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000003");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatorApprovalService creatorApprovalService;

    @Test
    @DisplayName("MASTER는 창작자 가입을 승인할 수 있다")
    void when_master_approves_creator_success_response_is_returned() throws Exception {
        when(creatorApprovalService.review(any(), any(), any(CreatorApprovalRequest.class)))
                .thenReturn(new CreatorApprovalResponse(
                        CREATOR_ID,
                        ApprovalStatus.APPROVED,
                        ADMIN_ID,
                        LocalDateTime.parse("2026-09-10T02:00:00")));

        mockMvc.perform(withGatewayHeaders(request("APPROVED"), "MASTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("창작자 가입이 승인되었습니다."))
                .andExpect(jsonPath("$.data.creatorId").value(CREATOR_ID.toString()))
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").value(ADMIN_ID.toString()));
    }

    @Test
    @DisplayName("MANAGER는 창작자 가입을 거절할 수 있다")
    void when_manager_rejects_creator_success_response_is_returned() throws Exception {
        when(creatorApprovalService.review(any(), any(), any(CreatorApprovalRequest.class)))
                .thenReturn(new CreatorApprovalResponse(
                        CREATOR_ID, ApprovalStatus.REJECTED, null, null));

        mockMvc.perform(withGatewayHeaders(request("REJECTED"), "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("창작자 가입이 거절되었습니다."))
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.approvedBy").isEmpty())
                .andExpect(jsonPath("$.data.approvedAt").isEmpty());
    }

    @Test
    @DisplayName("CUSTOMER는 창작자 가입을 심사할 수 없다")
    void when_customer_reviews_creator_forbidden_response_is_returned() throws Exception {
        mockMvc.perform(withGatewayHeaders(request("APPROVED"), "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0008"));
    }

    @Test
    @DisplayName("PENDING을 요청하면 400을 반환한다")
    void when_pending_is_requested_bad_request_is_returned() throws Exception {
        mockMvc.perform(withGatewayHeaders(request("PENDING"), "MASTER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }

    @Test
    @DisplayName("존재하지 않는 창작자 심사는 404를 반환한다")
    void when_creator_does_not_exist_not_found_response_is_returned() throws Exception {
        when(creatorApprovalService.review(any(), any(), any(CreatorApprovalRequest.class)))
                .thenThrow(new BusinessException(CreatorErrorCode.CREATOR_NOT_FOUND));

        mockMvc.perform(withGatewayHeaders(request("APPROVED"), "MASTER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CREATOR_0001"));
    }

    private MockHttpServletRequestBuilder request(String approvalStatus) {
        return patch("/api/v1/admin/creators/{creatorId}/approval", CREATOR_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approvalStatus\":\"" + approvalStatus + "\"}");
    }

    private MockHttpServletRequestBuilder withGatewayHeaders(
            MockHttpServletRequestBuilder request, String role) {
        return request
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", role)
                .header("X-Token-Id", TOKEN_ID)
                .header("X-Token-Expires-At", "4102444800");
    }
}
