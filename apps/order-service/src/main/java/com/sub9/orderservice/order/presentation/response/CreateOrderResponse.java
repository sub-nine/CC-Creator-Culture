package com.sub9.orderservice.order.presentation.response;

import com.sub9.orderservice.order.domain.model.OrderStatus;
import java.time.Instant;

public record CreateOrderResponse(
        String orderNumber,
        OrderStatus status,
        long originalAmount,
        long discountAmount,
        long paymentAmount,
        Instant expiresAt
) {
}
