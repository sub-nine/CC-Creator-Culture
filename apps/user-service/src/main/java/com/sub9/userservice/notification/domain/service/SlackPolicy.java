package com.sub9.userservice.notification.domain.service;

import com.sub9.userservice.notification.domain.model.NotificationContext;

import java.util.Optional;

public class SlackPolicy {

    public Optional<String> destination(NotificationContext context) {
        return switch (context.eventType()) {
            case PAYMENT_PAID -> equalsIgnoreCase(context.paymentStatus(), "PAID")
                    ? Optional.of("PAYMENT_ALERT") : Optional.empty();
            case PAYMENT_FAILED -> isFinalPaymentFailure(context.paymentStatus())
                    ? Optional.of("PAYMENT_ALERT") : Optional.empty();
            case ORDER_CANCELLED -> equalsIgnoreCase(context.cancellationScope(), "FULL")
                    ? Optional.of("ORDER_ALERT") : Optional.empty();
            default -> Optional.empty();
        };
    }

    private boolean isFinalPaymentFailure(String paymentStatus) {
        return equalsIgnoreCase(paymentStatus, "FAILED")
                || equalsIgnoreCase(paymentStatus, "EXPIRED")
                || equalsIgnoreCase(paymentStatus, "PAYMENT_FAILED");
    }

    private boolean equalsIgnoreCase(String actual, String expected) {
        return actual != null && actual.equalsIgnoreCase(expected);
    }
}
