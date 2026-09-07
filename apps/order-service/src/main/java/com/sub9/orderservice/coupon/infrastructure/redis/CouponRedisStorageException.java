package com.sub9.orderservice.coupon.infrastructure.redis;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;

public class CouponRedisStorageException extends BusinessException {

    public CouponRedisStorageException() {
        super(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}
