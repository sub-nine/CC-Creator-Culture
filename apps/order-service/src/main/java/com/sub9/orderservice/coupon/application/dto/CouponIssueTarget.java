package com.sub9.orderservice.coupon.application.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CouponIssueTarget(UUID couponId, Instant expiredAt) {
    // 조회와 사전 검증을 통과한 쿠폰 정보

    public CouponIssueTarget {
        Objects.requireNonNull(couponId, "쿠폰 식별자는 필수입니다.");
        Objects.requireNonNull(expiredAt, "쿠폰 만료 시각은 필수입니다.");
    }
}
