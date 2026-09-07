package com.sub9.orderservice.coupon.application.port;

import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import java.util.UUID;

public interface CouponIssueProcessor {
    // DB 발급 처리 계약

    UUID process(CouponReservation reservation);
}
