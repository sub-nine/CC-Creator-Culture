package com.sub9.orderservice.payment.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.common.dto.response.ErrorResponse;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.payment.application.service.MockPaymentService;
import com.sub9.orderservice.payment.domain.exception.PaymentErrorCode;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import com.sub9.orderservice.payment.presentation.request.MockPaymentRequest;
import com.sub9.orderservice.payment.presentation.response.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders/{orderNumber}/payments")
public class PaymentController {

    private final MockPaymentService paymentService;

    @PostMapping
    public ApiResponse<PaymentResponse> process(
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
            @PathVariable String orderNumber,
            @Valid @RequestBody MockPaymentRequest request) {
        var result = paymentService.process(
                principal.userId(), parseOrderNumber(orderNumber), PaymentStatus.valueOf(request.result()));
        // 실패 기록의 커밋이 끝난 뒤 HTTP 오류로 변환합니다.
        if (result.status() == PaymentStatus.FAILED) {
            throw new BusinessException(PaymentErrorCode.MOCK_PAYMENT_FAILED);
        }
        return ApiResponse.success("모의 결제 성공", PaymentResponse.from(result));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.from(CommonErrorCode.VALIDATION_ERROR));
    }

    private static OrderNumber parseOrderNumber(String value) {
        try {
            return OrderNumber.from(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
    }
}
