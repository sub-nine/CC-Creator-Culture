package com.sub9.orderservice.order.domain.model;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    COMPLETED,
    EXPIRED,
    FAILED,
    CANCELED
}
