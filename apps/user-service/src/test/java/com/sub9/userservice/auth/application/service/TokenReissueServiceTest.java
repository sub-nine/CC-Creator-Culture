package com.sub9.userservice.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.userservice.auth.application.port.output.TokenProvider;
import com.sub9.userservice.auth.domain.exception.AuthErrorCode;
import com.sub9.userservice.auth.domain.exception.AuthenticationTokenStorageException;
import com.sub9.userservice.auth.domain.exception.InvalidTokenException;
import com.sub9.userservice.auth.domain.model.TokenClaims;
import com.sub9.userservice.auth.domain.model.TokenType;
import com.sub9.userservice.auth.domain.repository.AuthenticationTokenRepository;
import com.sub9.userservice.auth.presentation.request.TokenReissueRequest;
import com.sub9.userservice.auth.presentation.response.TokenReissueResponse;
import com.sub9.userservice.user.domain.model.UserRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Access Token 재발급 서비스")
class TokenReissueServiceTest {

    private static final UUID USER_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private static final UUID TOKEN_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000002");
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String ACCESS_TOKEN = "new-access-token";
    private static final Duration ACCESS_EXPIRATION = Duration.ofMinutes(30);
    private static final TokenClaims CLAIMS = new TokenClaims(
            USER_ID,
            UserRole.CUSTOMER,
            TOKEN_ID,
            Instant.parse("2026-09-03T00:00:00Z"),
            Instant.parse("2026-09-10T00:00:00Z"),
            TokenType.REFRESH);

    @Mock
    private TokenProvider tokenProvider;
    @Mock
    private AuthenticationTokenRepository authenticationTokenRepository;

    private TokenReissueService tokenReissueService;

    @BeforeEach
    void setUp() {
        tokenReissueService =
                new TokenReissueService(tokenProvider, authenticationTokenRepository);
    }

    @Test
    @DisplayName("검증된 Refresh Token이 Redis 값과 일치하면 새 Access Token을 발급한다")
    void reissues_access_token_when_refresh_token_matches() {
        givenValidRefreshToken();
        when(tokenProvider.issueAccessToken(USER_ID, UserRole.CUSTOMER)).thenReturn(ACCESS_TOKEN);
        when(tokenProvider.accessTokenExpiration()).thenReturn(ACCESS_EXPIRATION);

        TokenReissueResponse response =
                tokenReissueService.reissue(new TokenReissueRequest(REFRESH_TOKEN));

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);
        verify(authenticationTokenRepository, never())
                .saveRefreshToken(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
        verify(authenticationTokenRepository, never())
                .deleteRefreshToken(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("JWT 검증 실패는 AUTH_0002로 변환하고 Redis를 조회하지 않는다")
    void rejects_invalid_refresh_token_before_redis_lookup() {
        when(tokenProvider.validateRefreshToken(REFRESH_TOKEN)).thenThrow(new InvalidTokenException());

        assertAuthError(() -> reissue(REFRESH_TOKEN));
        verify(authenticationTokenRepository, never())
                .findRefreshToken(org.mockito.ArgumentMatchers.any());
        verify(tokenProvider, never())
                .issueAccessToken(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Redis에 Refresh Token이 없으면 AUTH_0002로 처리한다")
    void rejects_when_refresh_token_is_not_stored() {
        when(tokenProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);
        when(authenticationTokenRepository.findRefreshToken(USER_ID)).thenReturn(Optional.empty());

        assertAuthError(() -> reissue(REFRESH_TOKEN));
        verify(tokenProvider, never()).issueAccessToken(USER_ID, UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("요청과 Redis의 Refresh Token이 다르면 AUTH_0002로 처리한다")
    void rejects_when_refresh_token_does_not_match() {
        when(tokenProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);
        when(authenticationTokenRepository.findRefreshToken(USER_ID))
                .thenReturn(Optional.of("different-refresh-token"));

        assertAuthError(() -> reissue(REFRESH_TOKEN));
        verify(tokenProvider, never()).issueAccessToken(USER_ID, UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("Redis 조회 장애는 COMMON_0009를 그대로 전달한다")
    void propagates_redis_failure_as_service_unavailable() {
        when(tokenProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);
        when(authenticationTokenRepository.findRefreshToken(USER_ID))
                .thenThrow(new AuthenticationTokenStorageException());

        assertThatThrownBy(() -> reissue(REFRESH_TOKEN))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(CommonErrorCode.SERVICE_UNAVAILABLE))
                .hasMessageNotContaining(REFRESH_TOKEN);
        verify(tokenProvider, never()).issueAccessToken(USER_ID, UserRole.CUSTOMER);
    }

    private void givenValidRefreshToken() {
        when(tokenProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(CLAIMS);
        when(authenticationTokenRepository.findRefreshToken(USER_ID))
                .thenReturn(Optional.of(REFRESH_TOKEN));
    }

    private TokenReissueResponse reissue(String refreshToken) {
        return tokenReissueService.reissue(new TokenReissueRequest(refreshToken));
    }

    private void assertAuthError(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(AuthErrorCode.INVALID_REFRESH_TOKEN))
                .hasMessageNotContaining(REFRESH_TOKEN);
    }
}
