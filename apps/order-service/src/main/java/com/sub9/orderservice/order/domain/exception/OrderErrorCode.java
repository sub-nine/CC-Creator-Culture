package com.sub9.orderservice.order.domain.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {
    INVALID_ORDER_ITEMS("ORDER_0009", HttpStatus.BAD_REQUEST, "주문 상품 정보가 올바르지 않습니다."),
    INVALID_ORDER_AMOUNT("ORDER_0010", HttpStatus.BAD_REQUEST, "주문 금액이 올바르지 않습니다.");

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
