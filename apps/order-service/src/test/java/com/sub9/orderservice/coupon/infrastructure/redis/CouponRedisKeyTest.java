package com.sub9.orderservice.coupon.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("쿠폰 Redis 키")
class CouponRedisKeyTest {

    @Test
    @DisplayName("쿠폰 식별자로 잔여 수량 키를 생성한다")
    void creates_remaining_quantity_key() {
        UUID couponId = UUID.fromString("01990a00-0000-7000-8000-000000000001");

        assertThat(CouponRedisKey.remaining(couponId))
                .isEqualTo("coupon:01990a00-0000-7000-8000-000000000001:remaining");
    }

    @Test
    @DisplayName("쿠폰과 사용자 식별자로 사용자 선점 키를 생성한다")
    void creates_issued_user_key() {
        UUID couponId = UUID.fromString("01990a00-0000-7000-8000-000000000001");
        UUID userId = UUID.fromString("01990a00-0000-7000-8000-000000000002");

        assertThat(CouponRedisKey.issued(couponId, userId))
                .isEqualTo("coupon:01990a00-0000-7000-8000-000000000001:issued:"
                        + "01990a00-0000-7000-8000-000000000002");
    }
}
