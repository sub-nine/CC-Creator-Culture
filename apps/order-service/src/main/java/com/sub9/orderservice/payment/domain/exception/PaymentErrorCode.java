package com.sub9.orderservice.payment.domain.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {
    MOCK_PAYMENT_FAILED("PAYMENT_0001", HttpStatus.BAD_REQUEST, "모의 결제에 실패했습니다."),
    INVALID_PAYMENT_CANCELLATION("PAYMENT_0002", HttpStatus.CONFLICT, "결제에 성공한 경우에만 한 번 취소할 수 있습니다.");

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
