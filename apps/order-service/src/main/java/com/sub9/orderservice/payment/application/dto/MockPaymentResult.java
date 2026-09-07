package com.sub9.orderservice.payment.application.dto;

import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.payment.domain.model.Payment;
import com.sub9.orderservice.payment.domain.model.PaymentMethod;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record MockPaymentResult(
        UUID paymentId,
        String orderNumber,
        PaymentMethod method,
        PaymentStatus status,
        long amount,
        String failureCode,
        Instant processedAt
) {

    public static MockPaymentResult from(Payment payment, OrderNumber orderNumber) {
        return new MockPaymentResult(
                payment.getId(), orderNumber.toString(), payment.getMethod(), payment.getStatus(),
                payment.getAmount().getAmount(), payment.getFailureCode(), payment.getProcessedAt());
    }
}
