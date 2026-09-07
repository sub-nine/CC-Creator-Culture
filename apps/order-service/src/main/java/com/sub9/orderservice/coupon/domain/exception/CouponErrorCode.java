package com.sub9.orderservice.coupon.domain.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum CouponErrorCode implements ErrorCode {
    COUPON_NOT_FOUND("COUPON_0001", HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없습니다."),
    NOT_IN_ISSUE_PERIOD("COUPON_0002", HttpStatus.BAD_REQUEST, "쿠폰 발급 가능 기간이 아닙니다."),
    SOLD_OUT("COUPON_0003", HttpStatus.CONFLICT, "쿠폰 수량이 모두 소진되었습니다."),
    ALREADY_ISSUED("COUPON_0004", HttpStatus.CONFLICT, "이미 발급받은 쿠폰입니다."),
    COUPON_NOT_MODIFIABLE("COUPON_0005", HttpStatus.CONFLICT, "발급된 쿠폰은 변경할 수 없습니다."),
    COUPON_NOT_DELETABLE("COUPON_0006", HttpStatus.CONFLICT, "발급된 쿠폰은 삭제할 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    @Override
    public String code() {
        return code;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
