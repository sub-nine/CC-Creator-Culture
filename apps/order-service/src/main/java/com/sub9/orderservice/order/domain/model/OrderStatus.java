package com.sub9.orderservice.order.domain.model;

import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    COMPLETED,
    EXPIRED,
    FAILED,
    CANCELED;

    private static final Set<OrderStatus> CREATOR_VISIBLE_STATUSES = Set.of(
            PAID,
            PROCESSING,
            COMPLETED,
            CANCELED);

    public static Set<OrderStatus> creatorVisibleStatuses() {
        return CREATOR_VISIBLE_STATUSES;
    }

    public boolean isVisibleToCreator() {
        return CREATOR_VISIBLE_STATUSES.contains(this);
    }
}
