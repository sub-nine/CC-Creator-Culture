package com.sub9.orderservice.coupon.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.orderservice.coupon.application.event.CouponCreatedEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 Redis 잔여 수량 초기화")
class RedisCouponRemainingQuantityInitializerTest {

    private static final UUID COUPON_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    private RedisCouponRemainingQuantityInitializer initializer;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        initializer = new RedisCouponRemainingQuantityInitializer(redisTemplate);
    }

    @Test
    @DisplayName("쿠폰 전체 수량을 Redis 잔여 수량으로 저장한다")
    void initializes_remaining_quantity() {
        initializer.initialize(new CouponCreatedEvent(COUPON_ID, 100));

        verify(valueOperations).set(CouponRedisKey.remaining(COUPON_ID), "100");
    }

    @Test
    @DisplayName("Redis 저장 실패는 전파하지 않고 발급 시 지연 초기화에 맡긴다")
    void does_not_propagate_redis_failure() {
        doThrow(new QueryTimeoutException("Redis timeout"))
                .when(valueOperations).set(CouponRedisKey.remaining(COUPON_ID), "100");

        assertThatCode(() -> initializer.initialize(new CouponCreatedEvent(COUPON_ID, 100)))
                .doesNotThrowAnyException();
    }
}
