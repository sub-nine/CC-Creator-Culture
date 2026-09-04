package com.sub9.orderservice.order.application.service;

import java.util.Objects;

public record OrderCreationResult(int httpStatus, Object responseBody) {

    public OrderCreationResult {
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("HTTP 상태가 올바르지 않습니다.");
        }
        Objects.requireNonNull(responseBody, "주문 생성 응답은 필수입니다.");
    }
}
