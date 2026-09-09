package com.sub9.userservice.notification.application.port;

import java.util.UUID;
import java.util.List;

/** Implement using an order-service lookup when its notification lookup contract is available. */
public interface OrderNotificationLookup {

    OrderNotificationInfo findByOrderId(UUID orderId);

    // sellerUserIds are login user IDs, not Creator entity IDs.
    record OrderNotificationInfo(UUID buyerId, String orderNumber, List<UUID> sellerUserIds) {
        public OrderNotificationInfo {
            if (buyerId == null) {
                throw new IllegalArgumentException("buyerId is required");
            }
            sellerUserIds = sellerUserIds == null ? List.of() : List.copyOf(sellerUserIds);
        }
    }
}
