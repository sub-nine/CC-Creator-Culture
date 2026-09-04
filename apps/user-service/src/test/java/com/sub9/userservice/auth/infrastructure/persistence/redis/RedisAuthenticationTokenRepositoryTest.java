package com.sub9.userservice.auth.infrastructure.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.CommonErrorCode;
import com.sub9.userservice.auth.domain.exception.AuthenticationTokenStorageException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@DisplayName("Redis 인증 토큰 저장소")
// Redis 인증 토큰 저장소의 호출 및 장애 변환을 검증하는 단위 테스트
class RedisAuthenticationTokenRepositoryTest {

    private static final UUID USER_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private static final String KEY = "auth:refresh:" + USER_ID;
    private static final String REFRESH_TOKEN = "refresh-token-value";
    private static final Duration TTL = Duration.ofDays(7);

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private AuthenticationTokenLogoutScript logoutScript;
    private RedisScript<Long> redisScript;
    private RedisAuthenticationTokenRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        logoutScript = mock(AuthenticationTokenLogoutScript.class);
        redisScript = mock(RedisScript.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(logoutScript.value()).thenReturn(redisScript);
        repository = new RedisAuthenticationTokenRepository(redisTemplate, logoutScript);
    }

    @Test
    @DisplayName("Refresh Token을 사용자 키와 TTL로 저장한다")
    void saves_refresh_token_with_ttl() {
        repository.saveRefreshToken(USER_ID, REFRESH_TOKEN, TTL);

        verify(valueOperations).set(KEY, REFRESH_TOKEN, TTL);
    }

    @Test
    @DisplayName("저장된 Refresh Token을 조회한다")
    void finds_saved_refresh_token() {
        when(valueOperations.get(KEY)).thenReturn(REFRESH_TOKEN);

        Optional<String> result = repository.findRefreshToken(USER_ID);

        assertThat(result).contains(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Refresh Token 키가 없으면 빈 결과를 반환한다")
    void returns_empty_when_refresh_token_does_not_exist() {
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThat(repository.findRefreshToken(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("사용자 키의 Refresh Token을 삭제한다")
    void deletes_refresh_token() {
        repository.deleteRefreshToken(USER_ID);

        verify(redisTemplate).delete(KEY);
    }

    @Test
    @DisplayName("Redis 저장 실패를 공통 503 오류로 변환하고 토큰을 노출하지 않는다")
    void converts_redis_write_failure_to_service_unavailable() {
        when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("Redis timeout"));

        assertStorageFailure(() -> repository.saveRefreshToken(USER_ID, REFRESH_TOKEN, TTL));
    }

    @Test
    @DisplayName("Redis 조회 실패를 공통 503 오류로 변환한다")
    void converts_redis_read_failure_to_service_unavailable() {
        when(valueOperations.get(KEY)).thenThrow(new QueryTimeoutException("Redis timeout"));

        assertStorageFailure(() -> repository.findRefreshToken(USER_ID));
    }

    @Test
    @DisplayName("Redis 삭제 실패를 공통 503 오류로 변환한다")
    void converts_redis_delete_failure_to_service_unavailable() {
        when(redisTemplate.delete(KEY)).thenThrow(new QueryTimeoutException("Redis timeout"));

        assertStorageFailure(() -> repository.deleteRefreshToken(USER_ID));
    }

    @Test
    @DisplayName("Lua Script에 Refresh Token과 블랙리스트 키 및 TTL을 전달한다")
    void executes_logout_lua_script_with_contract_values() {
        UUID accessTokenId = UUID.fromString("0198f2a0-76c0-7000-8000-000000000002");
        String blacklistKey = "auth:blacklist:access:" + accessTokenId;
        when(redisTemplate.execute(
                redisScript,
                List.of(KEY, blacklistKey),
                "logout",
                "660"))
                .thenReturn(1L);

        repository.logout(USER_ID, accessTokenId, Duration.ofSeconds(660));

        verify(redisTemplate).execute(
                redisScript,
                List.of(KEY, blacklistKey),
                "logout",
                "660");
    }

    @Test
    @DisplayName("Lua Script 결과가 없으면 공통 503 오류로 처리한다")
    void converts_missing_lua_result_to_service_unavailable() {
        UUID accessTokenId = UUID.fromString("0198f2a0-76c0-7000-8000-000000000002");

        assertStorageFailure(
                () -> repository.logout(USER_ID, accessTokenId, Duration.ofSeconds(660)));
    }

    @Test
    @DisplayName("Lua Script 실행 장애를 공통 503 오류로 변환한다")
    void converts_lua_failure_to_service_unavailable() {
        UUID accessTokenId = UUID.fromString("0198f2a0-76c0-7000-8000-000000000002");
        when(redisTemplate.execute(
                redisScript,
                List.of(KEY, "auth:blacklist:access:" + accessTokenId),
                "logout",
                "660"))
                .thenThrow(new QueryTimeoutException("Redis timeout"));

        assertStorageFailure(
                () -> repository.logout(USER_ID, accessTokenId, Duration.ofSeconds(660)));
    }

    private void assertStorageFailure(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(AuthenticationTokenStorageException.class)
                .satisfies(exception -> assertThat(((AuthenticationTokenStorageException) exception)
                                .getErrorCode())
                        .isSameAs(CommonErrorCode.SERVICE_UNAVAILABLE))
                .hasMessage(CommonErrorCode.SERVICE_UNAVAILABLE.message())
                .hasMessageNotContaining(REFRESH_TOKEN);
    }
}
