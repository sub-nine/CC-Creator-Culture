package com.sub9.orderservice.payment.presentation.response;

import com.sub9.orderservice.payment.application.dto.MockPaymentResult;
import com.sub9.orderservice.payment.domain.model.PaymentMethod;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        String orderNumber,
        PaymentStatus status,
        PaymentMethod method,
        long amount,
        Instant processedAt
) {

    public static PaymentResponse from(MockPaymentResult result) {
        return new PaymentResponse(result.paymentId(), result.orderNumber(), result.status(),
                result.method(), result.amount(), result.processedAt());
    }
}
