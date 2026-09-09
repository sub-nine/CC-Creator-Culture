package com.sub9.orderservice.order.infrastructure.client;

import com.sub9.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

enum ProductStockErrorCode implements ErrorCode {
    INSUFFICIENT_STOCK("STOCK_0002", HttpStatus.CONFLICT, "재고가 부족합니다."),
    SKU_NOT_FOUND("SKU_0004", HttpStatus.NOT_FOUND, "존재하지 않는 옵션입니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ProductStockErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
    public String message() { return message; }
}
