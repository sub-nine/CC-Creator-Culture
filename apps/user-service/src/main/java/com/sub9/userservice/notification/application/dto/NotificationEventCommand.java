package com.sub9.userservice.notification.application.dto;

import com.sub9.userservice.notification.domain.model.EventType;
import com.sub9.userservice.notification.domain.model.NotificationContext;
import com.sub9.userservice.notification.domain.model.ReferenceType;
import com.sub9.userservice.notification.domain.model.SourceService;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NotificationEventCommand(
        UUID eventId,
        EventType eventType,
        SourceService sourceService,
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
        Boolean firstPublished,
        Instant occurredAt
) {

    public NotificationEventCommand {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(sourceService, "sourceService must not be null");
        Objects.requireNonNull(referenceType, "referenceType must not be null");
        Objects.requireNonNull(referenceId, "referenceId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public NotificationContext toContext() {
        return new NotificationContext(
                eventId,
                eventType,
                referenceType,
                referenceId,
                creatorId,
                buyerId,
                sellerId,
                followedUserId,
                productName,
                orderNumber,
                currentStock,
                paymentStatus,
                cancellationScope,
                productStatus,
                firstPublished
        );
    }
}
