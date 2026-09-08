package com.sub9.orderservice.coupon.application.event;

import java.util.Objects;
import java.util.UUID;

// DB 커밋 후 Redis 잔여 수량을 초기화할 쿠폰 정보를 전달
public record CouponCreatedEvent(UUID couponId, int totalQuantity) {
    public CouponCreatedEvent {
        Objects.requireNonNull(couponId, "쿠폰 식별자는 필수입니다.");
        if (totalQuantity < 1) {
            throw new IllegalArgumentException("쿠폰 전체 수량은 1 이상이어야 합니다.");
        }
    }
}
