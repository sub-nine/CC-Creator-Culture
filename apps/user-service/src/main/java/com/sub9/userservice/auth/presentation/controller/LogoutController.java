package com.sub9.userservice.auth.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.userservice.auth.application.service.LogoutService;
import com.sub9.userservice.user.domain.model.UserRole;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ApiResponse<Void> logout(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") UserRole userRole,
            @RequestHeader("X-Token-Id") UUID accessTokenId,
            @RequestHeader("X-Token-Expires-At") long expiresAtEpochSecond) {
        logoutService.logout(userId, accessTokenId, expiresAtEpochSecond);
        return ApiResponse.success("로그아웃이 완료되었습니다.", null);
    }
}
