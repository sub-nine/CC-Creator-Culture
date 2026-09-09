package com.sub9.gateway.auth.infrastructure.redis;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.gateway.auth.domain.exception.AuthenticationServiceUnavailableException;
import com.sub9.gateway.auth.domain.exception.InvalidAccessTokenException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("Redis Access Token 블랙리스트 검증")
class RedisAccessTokenBlacklistCheckerTest {

    private static final UUID TOKEN_ID =
            UUID.fromString("01992d35-8600-7000-8000-000000000002");
    private static final String BLACKLIST_KEY = "auth:blacklist:access:" + TOKEN_ID;

    private ReactiveStringRedisTemplate redisTemplate;
    private RedisAccessTokenBlacklistChecker checker;

    @BeforeEach
    void setUp() {
        redisTemplate = org.mockito.Mockito.mock(ReactiveStringRedisTemplate.class);
        checker = new RedisAccessTokenBlacklistChecker(redisTemplate);
    }

    @Test
    @DisplayName("블랙리스트 키가 없으면 검증에 성공한다")
    void when_token_is_not_blacklisted_then_completes() {
        when(redisTemplate.hasKey(BLACKLIST_KEY)).thenReturn(Mono.just(false));

        StepVerifier.create(checker.verifyNotBlacklisted(TOKEN_ID))
                .verifyComplete();

        verify(redisTemplate).hasKey(BLACKLIST_KEY);
    }

    @Test
    @DisplayName("블랙리스트 키가 있으면 Access Token을 거부한다")
    void when_token_is_blacklisted_then_rejects_token() {
        when(redisTemplate.hasKey(BLACKLIST_KEY)).thenReturn(Mono.just(true));

        StepVerifier.create(checker.verifyNotBlacklisted(TOKEN_ID))
                .expectError(InvalidAccessTokenException.class)
                .verify();
    }

    @Test
    @DisplayName("Redis 조회가 실패하면 인증 서비스 장애로 변환한다")
    void when_redis_query_fails_then_returns_service_unavailable_error() {
        when(redisTemplate.hasKey(BLACKLIST_KEY))
                .thenReturn(Mono.error(new QueryTimeoutException("timeout")));

        StepVerifier.create(checker.verifyNotBlacklisted(TOKEN_ID))
                .expectErrorSatisfies(error -> {
                    org.assertj.core.api.Assertions.assertThat(error)
                            .isInstanceOf(AuthenticationServiceUnavailableException.class)
                            .hasMessage("인증 서비스를 일시적으로 사용할 수 없습니다.")
                            .hasCauseInstanceOf(QueryTimeoutException.class)
                            .hasMessageNotContaining("timeout");
                })
                .verify();
    }
}
