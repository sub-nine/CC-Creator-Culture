package com.sub9.userservice.notification.domain.model;

import java.util.UUID;

public record NotificationContext(
        UUID eventId,
        EventType eventType,
        ReferenceType referenceType,
        UUID referenceId,
        UUID creatorId,
        UUID buyerId,
        UUID sellerId,
        UUID followedUserId,
        String productName,
        String orderNumber,
        Integer currentStock,
        String paymentStatus,
        String cancellationScope,
        String productStatus,
        Boolean firstPublished
) {
}
