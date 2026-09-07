package com.sub9.orderservice.coupon.application.dto;

import java.util.Objects;
import java.util.UUID;

public record CouponReservation(UUID couponId, UUID userId, UUID reservationId) {
    // 쿠폰·사용자·선점 ID

    public CouponReservation {
        Objects.requireNonNull(couponId, "쿠폰 식별자는 필수입니다.");
        Objects.requireNonNull(userId, "사용자 식별자는 필수입니다.");
        Objects.requireNonNull(reservationId, "선점 식별자는 필수입니다.");
    }
}
