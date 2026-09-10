package com.sub9.userservice.auth.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.userservice.auth.infrastructure.security.GatewayAuthenticationPrincipal;
import com.sub9.userservice.auth.application.service.ManagerAccountService;
import com.sub9.userservice.auth.presentation.request.CreateManagerRequest;
import com.sub9.userservice.auth.presentation.response.ManagerAccountResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/managers")
@RequiredArgsConstructor
public class AdminManagerController {

    private final ManagerAccountService managerAccountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ManagerAccountResponse> createManager(
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
            @Valid @RequestBody CreateManagerRequest request) {
        return ApiResponse.success(
                "MANAGER 계정이 생성되었습니다.",
                managerAccountService.createManager(principal.userId(), request));
    }
}
