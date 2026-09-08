package com.sub9.orderservice.coupon.presentation.request;

import com.sub9.orderservice.coupon.application.dto.CouponUpdateCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateCouponRequest(
        @Size(min = 1, max = 100, message = "쿠폰 이름은 1자 이상 100자 이하여야 합니다.")
        String couponName,
        @Min(value = 1, message = "할인율은 1 이상이어야 합니다.")
        @Max(value = 100, message = "할인율은 100 이하여야 합니다.")
        Integer discountRate,
        @Min(value = 1, message = "총 발급 수량은 1 이상이어야 합니다.")
        Integer totalQuantity,
        Instant startedAt,
        Instant expiredAt
) {
    public UpdateCouponRequest {
        couponName = couponName == null ? null : couponName.trim();
    }

    @AssertTrue(message = "최소 하나 이상의 쿠폰 수정 값이 필요합니다.")
    public boolean isAnyFieldPresent() {
        return couponName != null
                || discountRate != null
                || totalQuantity != null
                || startedAt != null
                || expiredAt != null;
    }

    @AssertTrue(message = "쿠폰 시작 시각은 만료 시각보다 빨라야 합니다.")
    public boolean isPeriodValid() {
        return startedAt == null || expiredAt == null || startedAt.isBefore(expiredAt);
    }

    public CouponUpdateCommand toCommand() {
        return new CouponUpdateCommand(
                couponName, discountRate, totalQuantity, startedAt, expiredAt);
    }
}
