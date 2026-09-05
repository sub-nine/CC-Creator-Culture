package com.sub9.orderservice.coupon.application.port;

import com.sub9.orderservice.coupon.application.dto.CouponIssueTarget;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;

public interface CouponIssueReserver {
    // Redis 선점·보상

    void reserve(CouponIssueTarget target, CouponReservation reservation);

    void rollback(CouponReservation reservation);
}
