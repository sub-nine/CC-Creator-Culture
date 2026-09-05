package com.sub9.userservice.auth.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.userservice.auth.application.service.LogoutService;
import com.sub9.userservice.auth.infrastructure.security.GatewayAuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.jwt", name = "secret")
public class LogoutController {
    // 내부 인증 헤더를 받아 로그아웃 요청을 처리하는 API 컨트롤러

    private final LogoutService logoutService;

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal GatewayAuthenticationPrincipal principal) {
        logoutService.logout(
                principal.userId(),
                principal.accessTokenId(),
                principal.expiresAtEpochSecond());
        return ApiResponse.success("로그아웃이 완료되었습니다.", null);
    }
}
