package com.sub9.userservice.auth.infrastructure.security;

import com.sub9.userservice.user.domain.model.UserRole;
import java.util.UUID;

public record GatewayAuthenticationPrincipal(
        // 검증된 사용자 ID, 역할, Access Token ID, 만료 시각

        UUID userId,
        UserRole role,
        UUID accessTokenId,
        long expiresAtEpochSecond) {
}
