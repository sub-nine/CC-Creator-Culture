package com.sub9.orderservice.coupon.application.exception;

public class CouponReservationReleaseRequiredException extends RuntimeException {

    public CouponReservationReleaseRequiredException(RuntimeException cause) {
        super(cause);
    }
}
