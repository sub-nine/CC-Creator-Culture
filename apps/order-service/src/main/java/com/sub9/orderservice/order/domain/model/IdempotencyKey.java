package com.sub9.orderservice.order.domain.model;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import java.util.regex.Pattern;

public record IdempotencyKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("[!-~]{1,100}");

    public IdempotencyKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
    }

    public static IdempotencyKey from(String value) {
        return new IdempotencyKey(value);
    }
}
