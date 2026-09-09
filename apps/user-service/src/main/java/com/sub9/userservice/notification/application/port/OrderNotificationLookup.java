package com.sub9.userservice.notification.application.port;

import java.util.UUID;

/** Implement using an order-service lookup when its notification lookup contract is available. */
public interface OrderNotificationLookup {

    OrderNotificationInfo findByOrderId(UUID orderId);

    record OrderNotificationInfo(UUID buyerId, String orderNumber) {
        public OrderNotificationInfo {
            if (buyerId == null) {
                throw new IllegalArgumentException("buyerId is required");
            }
        }
    }
}
