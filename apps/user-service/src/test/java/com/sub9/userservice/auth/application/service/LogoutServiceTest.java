package com.sub9.userservice.auth.application.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.userservice.auth.application.port.output.TokenProvider;
import com.sub9.userservice.auth.domain.repository.AuthenticationTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그아웃 서비스")
class LogoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T03:00:00Z");
    private static final UUID USER_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private static final UUID ACCESS_TOKEN_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000002");

    @Mock
    private AuthenticationTokenRepository authenticationTokenRepository;
    @Mock
    private TokenProvider tokenProvider;

    private LogoutService logoutService;

    @BeforeEach
    void setUp() {
        logoutService = new LogoutService(
                authenticationTokenRepository,
                tokenProvider,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(tokenProvider.clockSkew()).thenReturn(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("Access Token 남은 시간에 60초를 더한 블랙리스트 TTL로 로그아웃한다")
    void calculates_blacklist_ttl_with_clock_skew() {
        logoutService.logout(USER_ID, ACCESS_TOKEN_ID, NOW.plusSeconds(600).getEpochSecond());

        verify(authenticationTokenRepository)
                .logout(USER_ID, ACCESS_TOKEN_ID, Duration.ofSeconds(660));
    }

    @Test
    @DisplayName("보정 만료시간이 지난 토큰은 음수 TTL로 전달해 블랙리스트 저장을 생략한다")
    void passes_non_positive_ttl_for_expired_token() {
        logoutService.logout(USER_ID, ACCESS_TOKEN_ID, NOW.minusSeconds(61).getEpochSecond());

        verify(authenticationTokenRepository)
                .logout(USER_ID, ACCESS_TOKEN_ID, Duration.ofSeconds(-1));
    }
}
