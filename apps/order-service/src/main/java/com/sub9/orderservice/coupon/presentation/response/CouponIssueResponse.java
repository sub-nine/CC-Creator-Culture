package com.sub9.orderservice.coupon.presentation.response;

import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;
import java.util.UUID;

public record CouponIssueResponse(UUID userCouponId) {

    public static CouponIssueResponse from(IssueDispatchResult.Completed completed) {
        return new CouponIssueResponse(completed.userCouponId());
    }
}
