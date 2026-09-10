package com.sub9.gateway.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

// 검증에 성공한 정보만 담는 불변 객체
public record AccessTokenClaims(
        UUID userId,
        GatewayUserRole role,
        UUID tokenId,
        Instant issuedAt,
        Instant expiresAt) {
}
