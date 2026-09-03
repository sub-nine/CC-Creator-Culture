package com.sub9.userservice.notification.infrastructure.messaging.kafka;

import com.sub9.userservice.notification.application.dto.NotificationEventCommand;
import com.sub9.userservice.notification.domain.model.EventType;
import com.sub9.userservice.notification.domain.model.ReferenceType;
import com.sub9.userservice.notification.domain.model.SourceService;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record KafkaDomainEventPayload(
        @NotNull UUID eventId,
        @NotNull EventType eventType,
        @NotNull SourceService sourceService,
        @NotNull ReferenceType referenceType,
        @NotNull UUID referenceId,
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
        @NotNull Instant occurredAt
) {

    public NotificationEventCommand toCommand() {
        return new NotificationEventCommand(
                eventId,
                eventType,
                sourceService,
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
                firstPublished,
                occurredAt
        );
    }
}
