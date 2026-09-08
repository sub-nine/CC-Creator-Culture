package com.sub9.orderservice.coupon.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.orderservice.coupon.application.event.CouponCreatedEvent;
import com.sub9.orderservice.coupon.application.event.CouponDeletedEvent;
import com.sub9.orderservice.coupon.application.event.CouponUpdatedEvent;
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
        initializer = new RedisCouponRemainingQuantityInitializer(redisTemplate);
    }

    @Test
    @DisplayName("쿠폰 전체 수량을 Redis 잔여 수량으로 저장한다")
    void initializes_remaining_quantity() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        initializer.initialize(new CouponCreatedEvent(COUPON_ID, 100));

        verify(valueOperations).set(CouponRedisKey.remaining(COUPON_ID), "100");
    }

    @Test
    @DisplayName("수정된 쿠폰 전체 수량으로 Redis 잔여 수량을 갱신한다")
    void updates_remaining_quantity() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        initializer.update(new CouponUpdatedEvent(COUPON_ID, 200));

        verify(valueOperations).set(CouponRedisKey.remaining(COUPON_ID), "200");
    }

    @Test
    @DisplayName("삭제된 쿠폰의 Redis 잔여 수량 키를 제거한다")
    void deletes_remaining_quantity() {
        initializer.delete(new CouponDeletedEvent(COUPON_ID));

        verify(redisTemplate).delete(CouponRedisKey.remaining(COUPON_ID));
    }

    @Test
    @DisplayName("Redis 수량 키 삭제 실패는 DB 삭제 결과에 영향을 주지 않는다")
    void does_not_propagate_delete_failure() {
        doThrow(new QueryTimeoutException("Redis timeout"))
                .when(redisTemplate).delete(CouponRedisKey.remaining(COUPON_ID));

        assertThatCode(() -> initializer.delete(new CouponDeletedEvent(COUPON_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 저장 실패는 전파하지 않고 발급 시 지연 초기화에 맡긴다")
    void does_not_propagate_redis_failure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new QueryTimeoutException("Redis timeout"))
                .when(valueOperations).set(CouponRedisKey.remaining(COUPON_ID), "100");

        assertThatCode(() -> initializer.initialize(new CouponCreatedEvent(COUPON_ID, 100)))
                .doesNotThrowAnyException();
    }
}
