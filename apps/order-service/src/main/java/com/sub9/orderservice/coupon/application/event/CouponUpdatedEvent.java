package com.sub9.orderservice.coupon.application.event;

import java.util.Objects;
import java.util.UUID;

public record CouponUpdatedEvent(UUID couponId, int totalQuantity) {

    public CouponUpdatedEvent {
        Objects.requireNonNull(couponId, "쿠폰 식별자는 필수입니다.");
        if (totalQuantity < 1) {
            throw new IllegalArgumentException("총 발급 수량은 1 이상이어야 합니다.");
        }
    }
}
