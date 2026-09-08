package com.sub9.orderservice.coupon.application.port;

import com.sub9.orderservice.coupon.application.dto.CouponIssueTarget;
import java.time.Instant;
import java.util.UUID;

public interface CouponIssueReader {
    // 발급 가능한 쿠폰 조회

    CouponIssueTarget getIssuable(UUID couponId, Instant requestedAt);
}
