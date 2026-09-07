package com.sub9.orderservice.coupon.application.port;

import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;

public interface CouponIssueDispatcher {
    // 선점 이후 처리 방식 선택

    IssueDispatchResult dispatch(CouponReservation reservation);
}
