package com.sub9.orderservice.coupon.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.orderservice.coupon.application.event.CouponCreatedEvent;
import com.sub9.orderservice.coupon.application.event.CouponDeletedEvent;
import com.sub9.orderservice.coupon.application.event.CouponUpdatedEvent;
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
@DisplayName("쿠폰 Redis 잔여 수량 초기화 통합")
class RedisCouponRemainingQuantityInitializerIntegrationTest {

    private static final UUID COUPON_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
                    DockerImageName.parse("redis:7.4.11-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisCouponRemainingQuantityInitializer initializer;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        initializer = new RedisCouponRemainingQuantityInitializer(redisTemplate);
    }

    @AfterAll
    static void tearDownRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("Redis에 잔여 수량을 저장하고 같은 쿠폰의 값을 갱신한다")
    void stores_and_replaces_remaining_quantity() {
        String key = CouponRedisKey.remaining(COUPON_ID);

        initializer.initialize(new CouponCreatedEvent(COUPON_ID, 100));
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("100");

        initializer.update(new CouponUpdatedEvent(COUPON_ID, 80));
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("80");
        assertThat(redisTemplate.getExpire(key)).isEqualTo(-1);
    }

    @Test
    @DisplayName("삭제된 쿠폰의 Redis 잔여 수량 키를 실제로 제거한다")
    void deletes_remaining_quantity_key() {
        String key = CouponRedisKey.remaining(COUPON_ID);
        initializer.initialize(new CouponCreatedEvent(COUPON_ID, 100));

        initializer.delete(new CouponDeletedEvent(COUPON_ID));

        assertThat(redisTemplate.hasKey(key)).isFalse();
    }
}
