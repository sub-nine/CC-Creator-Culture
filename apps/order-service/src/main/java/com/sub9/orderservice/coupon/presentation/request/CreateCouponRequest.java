package com.sub9.orderservice.coupon.presentation.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateCouponRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.")
        @Size(max = 100, message = "쿠폰 이름은 100자 이하여야 합니다.")
        String couponName,
        @NotNull(message = "할인율은 필수입니다.")
        @Min(value = 1, message = "할인율은 1 이상이어야 합니다.")
        @Max(value = 100, message = "할인율은 100 이하여야 합니다.")
        Integer discountRate,
        @NotNull(message = "총 발급 수량은 필수입니다.")
        @Min(value = 1, message = "총 발급 수량은 1 이상이어야 합니다.")
        Integer totalQuantity,
        @NotNull(message = "쿠폰 시작 시각은 필수입니다.") Instant startedAt,
        @NotNull(message = "쿠폰 만료 시각은 필수입니다.") Instant expiredAt
) {
    public CreateCouponRequest {
        couponName = couponName == null ? null : couponName.trim();
    }

    @AssertTrue(message = "쿠폰 시작 시각은 만료 시각보다 빨라야 합니다.")
    public boolean isPeriodValid() {
        return startedAt == null || expiredAt == null || startedAt.isBefore(expiredAt);
    }
}
