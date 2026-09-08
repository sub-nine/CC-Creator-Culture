package com.sub9.orderservice.coupon.application.dto;

import java.time.Instant;

public record CouponUpdateCommand(
        String couponName,
        Integer discountRate,
        Integer totalQuantity,
        Instant startedAt,
        Instant expiredAt
) {

    public CouponUpdateCommand {
        couponName = couponName == null ? null : couponName.trim();
        if (couponName == null
                && discountRate == null
                && totalQuantity == null
                && startedAt == null
                && expiredAt == null) {
            throw new IllegalArgumentException("최소 하나 이상의 쿠폰 수정 값이 필요합니다.");
        }
    }
}
