package com.sub9.orderservice.order.domain.model;

public enum OrderItemStatus {
    ORDERED,
    PREPARING,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELED;

    boolean isCreatorTarget() {
        return switch (this) {
            case PREPARING, SHIPPED, DELIVERED, COMPLETED -> true;
            case ORDERED, CANCELED -> false;
        };
    }

    boolean canTransitionTo(OrderItemStatus target) {
        return switch (this) {
            case ORDERED -> target == PREPARING;
            case PREPARING -> target == SHIPPED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED -> target == COMPLETED;
            case COMPLETED, CANCELED -> false;
        };
    }
}
