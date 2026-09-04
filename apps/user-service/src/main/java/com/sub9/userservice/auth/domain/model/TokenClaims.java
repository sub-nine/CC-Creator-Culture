package com.sub9.userservice.auth.domain.model;

import com.sub9.userservice.user.domain.model.UserRole;
import java.time.Instant;
import java.util.UUID;

public record TokenClaims(
        UUID userId,
        UserRole role,
        UUID tokenId,
        Instant issuedAt,
        Instant expiresAt,
        TokenType tokenType) {
}
