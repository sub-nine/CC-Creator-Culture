package com.sub9.gateway.auth.infrastructure.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.gateway.auth.domain.exception.AuthenticationServiceUnavailableException;
import com.sub9.gateway.auth.domain.exception.InvalidAccessTokenException;
import com.sub9.gateway.auth.domain.model.AccessTokenClaims;
import com.sub9.gateway.auth.domain.model.GatewayUserRole;
import com.sub9.gateway.auth.infrastructure.jwt.JwtAccessTokenValidator;
import com.sub9.gateway.auth.infrastructure.redis.RedisAccessTokenBlacklistChecker;
import com.sub9.gateway.auth.infrastructure.web.InternalAuthenticationHeaderSanitizer;
import com.sub9.gateway.auth.infrastructure.web.InternalAuthenticationHeaderWriter;
import com.sub9.gateway.auth.infrastructure.web.PublicAuthEndpointMatcher;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("Gateway JWT 인증 필터")
class GatewayAuthenticationFilterTest {

    private static final UUID USER_ID =
            UUID.fromString("01992d35-8600-7000-8000-000000000001");
    private static final UUID TOKEN_ID =
            UUID.fromString("01992d35-8600-7000-8000-000000000002");
    private static final Instant ISSUED_AT = Instant.parse("2026-09-09T04:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(1800);
    private static final AccessTokenClaims CLAIMS = new AccessTokenClaims(
            USER_ID, GatewayUserRole.CUSTOMER, TOKEN_ID, ISSUED_AT, EXPIRES_AT);

    private JwtAccessTokenValidator tokenValidator;
    private RedisAccessTokenBlacklistChecker blacklistChecker;
    private GatewayFilterChain chain;
    private GatewayAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenValidator = org.mockito.Mockito.mock(JwtAccessTokenValidator.class);
        blacklistChecker = org.mockito.Mockito.mock(RedisAccessTokenBlacklistChecker.class);
        chain = org.mockito.Mockito.mock(GatewayFilterChain.class);
        filter = new GatewayAuthenticationFilter(
                new PublicAuthEndpointMatcher(),
                new InternalAuthenticationHeaderSanitizer(),
                tokenValidator,
                blacklistChecker,
                new InternalAuthenticationHeaderWriter());
    }

    @Test
    @DisplayName("공개 요청은 외부 인증 헤더만 제거하고 JWT와 Redis 검증 없이 전달한다")
    void when_request_is_public_then_sanitizes_and_forwards_without_authentication() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/login")
                .header("X-User-Role", "MASTER")
                .build());
        AtomicReference<ServerWebExchange> forwarded = captureForwardedExchange();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role")).isNull();
        verify(tokenValidator, never()).validate(any());
        verify(blacklistChecker, never()).verifyNotBlacklisted(any());
    }

    @Test
    @DisplayName("정상 보호 요청은 JWT와 블랙리스트를 검증한 뒤 내부 인증 헤더를 전달한다")
    void when_protected_request_is_valid_then_forwards_verified_authentication_headers() {
        var exchange = protectedExchange("Bearer access-token", "spoofed-user", "MASTER");
        when(tokenValidator.validate("access-token")).thenReturn(CLAIMS);
        when(blacklistChecker.verifyNotBlacklisted(TOKEN_ID)).thenReturn(Mono.empty());
        AtomicReference<ServerWebExchange> forwarded = captureForwardedExchange();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo(USER_ID.toString());
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("CUSTOMER");
        assertThat(headers.getFirst("X-Token-Id")).isEqualTo(TOKEN_ID.toString());
        assertThat(headers.getFirst("X-Token-Expires-At"))
                .isEqualTo(Long.toString(EXPIRES_AT.getEpochSecond()));
        verify(tokenValidator).validate("access-token");
        verify(blacklistChecker).verifyNotBlacklisted(TOKEN_ID);
    }

    @Test
    @DisplayName("Bearer 인증 스킴은 대소문자를 구분하지 않는다")
    void when_bearer_scheme_has_different_case_then_authenticates_request() {
        var exchange = protectedExchange("bearer access-token", null, null);
        when(tokenValidator.validate("access-token")).thenReturn(CLAIMS);
        when(blacklistChecker.verifyNotBlacklisted(TOKEN_ID)).thenReturn(Mono.empty());
        captureForwardedExchange();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(tokenValidator).validate("access-token");
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 요청을 거부한다")
    void when_authorization_header_is_missing_then_rejects_request() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(InvalidAccessTokenException.class)
                .verify();

        verify(tokenValidator, never()).validate(any());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Bearer 형식이 아니거나 토큰이 비어 있으면 요청을 거부한다")
    void when_authorization_header_is_invalid_then_rejects_request() {
        var basicExchange = protectedExchange("Basic credentials", null, null);
        var emptyBearerExchange = protectedExchange("Bearer   ", null, null);

        StepVerifier.create(filter.filter(basicExchange, chain))
                .expectError(InvalidAccessTokenException.class)
                .verify();
        StepVerifier.create(filter.filter(emptyBearerExchange, chain))
                .expectError(InvalidAccessTokenException.class)
                .verify();

        verify(tokenValidator, never()).validate(any());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("JWT 검증에 실패하면 Redis를 조회하거나 다음 필터 체인을 호출하지 않는다")
    void when_jwt_validation_fails_then_stops_filter_chain() {
        var exchange = protectedExchange("Bearer invalid-token", null, null);
        when(tokenValidator.validate("invalid-token"))
                .thenThrow(new InvalidAccessTokenException());

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(InvalidAccessTokenException.class)
                .verify();

        verify(blacklistChecker, never()).verifyNotBlacklisted(any());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("블랙리스트 토큰이면 다음 필터 체인을 호출하지 않는다")
    void when_token_is_blacklisted_then_stops_filter_chain() {
        var exchange = protectedExchange("Bearer access-token", null, null);
        when(tokenValidator.validate("access-token")).thenReturn(CLAIMS);
        when(blacklistChecker.verifyNotBlacklisted(TOKEN_ID))
                .thenReturn(Mono.error(new InvalidAccessTokenException()));

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(InvalidAccessTokenException.class)
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Redis 장애는 인증 서비스 장애 예외로 전달한다")
    void when_redis_is_unavailable_then_propagates_service_unavailable_error() {
        var exchange = protectedExchange("Bearer access-token", null, null);
        var redisError = new AuthenticationServiceUnavailableException(
                new IllegalStateException("redis unavailable"));
        when(tokenValidator.validate("access-token")).thenReturn(CLAIMS);
        when(blacklistChecker.verifyNotBlacklisted(TOKEN_ID)).thenReturn(Mono.error(redisError));

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(AuthenticationServiceUnavailableException.class)
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("인증 필터는 가장 높은 우선순위로 실행된다")
    void authentication_filter_has_highest_precedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    private MockServerWebExchange protectedExchange(
            String authorization,
            String userId,
            String role) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.get("/api/v1/orders");
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (userId != null) {
            builder.header("X-User-Id", userId);
        }
        if (role != null) {
            builder.header("X-User-Role", role);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private AtomicReference<ServerWebExchange> captureForwardedExchange() {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        when(chain.filter(any())).thenAnswer(invocation -> {
            forwarded.set(invocation.getArgument(0));
            return Mono.empty();
        });
        return forwarded;
    }
}
