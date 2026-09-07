package com.sub9.orderservice.coupon.application.event;

import java.util.Objects;
import java.util.UUID;

public record CouponDeletedEvent(UUID couponId) {

    public CouponDeletedEvent {
        Objects.requireNonNull(couponId, "쿠폰 식별자는 필수입니다.");
    }
}
