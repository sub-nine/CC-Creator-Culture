package com.sub9.userservice.notification.domain.model;

import java.util.UUID;
import java.util.List;

public record NotificationContext(
        UUID eventId,
        EventType eventType,
        ReferenceType referenceType,
        UUID referenceId,
        UUID creatorId,
        UUID buyerId,
        List<UUID> sellerUserIds,
        UUID followedUserId,
        String productName,
        String orderNumber,
        Integer currentStock,
        String paymentStatus,
        String cancellationScope,
        String productStatus,
        Boolean firstPublished
) {
    public NotificationContext {
        // For cancellations, supply only the sellers affected by this cancellation.
        sellerUserIds = sellerUserIds == null ? List.of() : List.copyOf(sellerUserIds);
    }
}
