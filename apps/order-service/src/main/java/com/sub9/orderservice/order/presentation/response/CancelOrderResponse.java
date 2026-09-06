package com.sub9.orderservice.order.presentation.response;

import com.sub9.orderservice.order.domain.model.OrderStatus;
import java.time.Instant;

public record CancelOrderResponse(String orderNumber, OrderStatus status, Instant canceledAt) {
}
