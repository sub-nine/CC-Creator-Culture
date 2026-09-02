package com.sub9.userservice.auth.domain.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

// Refresh Token 저장·조회·삭제 기능을 정의하는 인증 토큰 저장소 인터페이스
public interface AuthenticationTokenRepository {

    void saveRefreshToken(UUID userId, String refreshToken, Duration ttl);

    Optional<String> findRefreshToken(UUID userId);

    void deleteRefreshToken(UUID userId);
}
