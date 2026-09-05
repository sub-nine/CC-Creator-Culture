package com.sub9.orderservice.coupon.presentation.response;

import com.sub9.orderservice.coupon.domain.model.Coupon;
import java.time.Instant;
import java.util.UUID;

public record CouponResponse(
        UUID couponId, String couponName, int discountRate, int totalQuantity,
        int issuedQuantity, Instant startedAt, Instant expiredAt
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(), coupon.getCouponName(), coupon.getDiscountRate(),
                coupon.getTotalQuantity(), coupon.getIssuedQuantity(),
                coupon.getStartedAt(), coupon.getExpiredAt());
    }
}
