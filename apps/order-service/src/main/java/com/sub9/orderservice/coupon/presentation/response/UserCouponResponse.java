package com.sub9.orderservice.coupon.presentation.response;

import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import java.time.Instant;
import java.util.UUID;

public record UserCouponResponse(
        UUID userCouponId,
        UUID couponId,
        String couponName,
        int discountRate,
        UserCouponStatus status,
        boolean expired,
        Instant issuedAt,
        Instant usedAt,
        Instant expiredAt
) {

    public static UserCouponResponse from(UserCoupon userCoupon, Instant now) {
        Coupon coupon = userCoupon.getCoupon();
        boolean expired = userCoupon.getStatus() == UserCouponStatus.ISSUED
                && now.isAfter(coupon.getExpiredAt());
        return new UserCouponResponse(
                userCoupon.getId(), coupon.getId(), coupon.getCouponName(),
                coupon.getDiscountRate(), userCoupon.getStatus(), expired,
                userCoupon.getIssuedAt(), userCoupon.getUsedAt(), coupon.getExpiredAt());
    }
}
