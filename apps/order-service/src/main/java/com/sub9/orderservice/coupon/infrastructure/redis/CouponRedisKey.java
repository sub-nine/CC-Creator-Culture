package com.sub9.orderservice.coupon.infrastructure.redis;

import java.util.Objects;
import java.util.UUID;
// 쿠폰 Redis 키 형식 관리
public final class CouponRedisKey {

    private static final String PREFIX = "coupon:";
    private static final String REMAINING_SUFFIX = ":remaining";
    private static final String ISSUED_INFIX = ":issued:";

    private CouponRedisKey() {}

    public static String remaining(UUID couponId) {
        return PREFIX + Objects.requireNonNull(couponId, "쿠폰 식별자는 필수입니다.") + REMAINING_SUFFIX;
    }

    public static String issued(UUID couponId, UUID userId) {
        return PREFIX + Objects.requireNonNull(couponId, "쿠폰 식별자는 필수입니다.")
                + ISSUED_INFIX + Objects.requireNonNull(userId, "사용자 식별자는 필수입니다.");
    }
}
