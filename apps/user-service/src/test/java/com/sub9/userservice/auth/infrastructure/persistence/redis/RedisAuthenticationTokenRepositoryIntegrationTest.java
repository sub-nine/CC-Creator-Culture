package com.sub9.userservice.auth.infrastructure.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("인증 토큰 Redis 저장소 통합")
// 실제 Redis 컨테이너에서 저장·교체·삭제·만료 동작을 검증하는 통합 테스트
class RedisAuthenticationTokenRepositoryIntegrationTest {

    private static final UUID USER_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private static final String KEY = AuthenticationTokenRedisKey.refreshToken(USER_ID);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
                    DockerImageName.parse("redis:7.4.11-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisAuthenticationTokenRepository repository;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new RedisAuthenticationTokenRepository(redisTemplate);
    }

    @AfterAll
    static void tearDownRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("Refresh Token을 저장하고 7일 TTL을 설정한다")
    void saves_refresh_token_with_expiration() {
        repository.saveRefreshToken(USER_ID, "first-refresh-token", Duration.ofDays(7));

        assertThat(repository.findRefreshToken(USER_ID)).contains("first-refresh-token");
        assertThat(redisTemplate.getExpire(KEY)).isBetween(Duration.ofDays(7).minusSeconds(5).toSeconds(), Duration.ofDays(7).toSeconds());
    }

    @Test
    @DisplayName("같은 사용자의 새 로그인은 기존 토큰과 TTL을 교체한다")
    void replaces_existing_refresh_token_and_ttl() {
        repository.saveRefreshToken(USER_ID, "old-refresh-token", Duration.ofDays(7));
        repository.saveRefreshToken(USER_ID, "new-refresh-token", Duration.ofHours(1));

        assertThat(repository.findRefreshToken(USER_ID)).contains("new-refresh-token");
        assertThat(redisTemplate.getExpire(KEY)).isBetween(Duration.ofMinutes(59).toSeconds(), Duration.ofHours(1).toSeconds());
    }

    @Test
    @DisplayName("Refresh Token 삭제는 키가 없어도 반복할 수 있다")
    void deletes_refresh_token_idempotently() {
        repository.saveRefreshToken(USER_ID, "refresh-token", Duration.ofDays(7));

        repository.deleteRefreshToken(USER_ID);
        repository.deleteRefreshToken(USER_ID);

        assertThat(repository.findRefreshToken(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("TTL이 지난 Refresh Token은 자동으로 만료된다")
    void expires_refresh_token_after_ttl() throws InterruptedException {
        repository.saveRefreshToken(USER_ID, "short-lived-token", Duration.ofMillis(100));

        Thread.sleep(150);

        assertThat(repository.findRefreshToken(USER_ID)).isEmpty();
    }
}
