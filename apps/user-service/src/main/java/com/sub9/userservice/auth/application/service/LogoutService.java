package com.sub9.userservice.auth.application.service;

import com.sub9.userservice.auth.application.port.output.TokenProvider;
import com.sub9.userservice.auth.domain.repository.AuthenticationTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.jwt", name = "secret")
public class LogoutService {
    // 블랙리스트 TTL을 계산하고 Redis 로그아웃 처리를 요청하는 서비스

    private final AuthenticationTokenRepository authenticationTokenRepository;
    private final TokenProvider tokenProvider;
    private final Clock clock;

    public void logout(UUID userId, UUID accessTokenId, long expiresAtEpochSecond) {
        Duration blacklistTtl = Duration.between(
                        clock.instant(), Instant.ofEpochSecond(expiresAtEpochSecond))
                .plus(tokenProvider.clockSkew());
        authenticationTokenRepository.logout(userId, accessTokenId, blacklistTtl);
    }
}
