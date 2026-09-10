package com.sub9.userservice.creator.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.userservice.auth.infrastructure.security.GatewayAuthenticationPrincipal;
import com.sub9.userservice.creator.application.service.CreatorApprovalService;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.presentation.request.CreatorApprovalRequest;
import com.sub9.userservice.creator.presentation.response.CreatorApprovalResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/creators")
@RequiredArgsConstructor
public class AdminCreatorApprovalController {

    private final CreatorApprovalService creatorApprovalService;

    @PatchMapping("/{creatorId}/approval")
    public ApiResponse<CreatorApprovalResponse> review(
            @PathVariable UUID creatorId,
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
            @Valid @RequestBody CreatorApprovalRequest request) {
        CreatorApprovalResponse response = creatorApprovalService.review(
                creatorId, principal.userId(), request);
        String message = response.approvalStatus() == ApprovalStatus.APPROVED
                ? "창작자 가입이 승인되었습니다."
                : "창작자 가입이 거절되었습니다.";
        return ApiResponse.success(message, response);
    }
}
