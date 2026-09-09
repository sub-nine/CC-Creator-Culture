package com.sub9.userservice.notification.infrastructure.messaging.kafka;

import com.sub9.common.kafka.event.OrderNotificationEvent;
import com.sub9.common.kafka.event.ProductCreatedEvent;
import com.sub9.userservice.notification.application.dto.NotificationEventCommand;
import com.sub9.userservice.notification.domain.model.EventType;
import com.sub9.userservice.notification.domain.model.ReferenceType;
import com.sub9.userservice.notification.domain.model.SourceService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class NotificationEventMapper {

    public NotificationEventCommand fromProductCreated(
            ProductCreatedEvent event, ConsumerRecord<String, String> record
    ) {
        validateRecord(record, event.productId());
        if (event.creatorId() == null) {
            throw new IllegalArgumentException("creatorId is required");
        }
        return new NotificationEventCommand(
                eventId(record), EventType.PRODUCT_CREATED, SourceService.PRODUCT_SERVICE,
                ReferenceType.PRODUCT, event.productId(), event.creatorId(),
                null, List.of(), null, event.name(), null, null, null, null, null, null,
                messageTimestamp(record)
        );
    }

    public NotificationEventCommand fromOrderNotification(OrderNotificationEvent event) {
        if (event.eventId() == null || event.referenceId() == null
                || event.buyerId() == null || event.occurredAt() == null) {
            throw new IllegalArgumentException("Required order notification fields are missing");
        }
        if (!"ORDER_SERVICE".equals(event.sourceService()) || !"ORDER".equals(event.referenceType())) {
            throw new IllegalArgumentException("Invalid event source or reference type");
        }
        if (event.orderNumber() == null || event.orderNumber().isBlank()) {
            throw new IllegalArgumentException("orderNumber is required");
        }
        return new NotificationEventCommand(
                event.eventId(), resolveOrderEventType(event), SourceService.ORDER_SERVICE,
                ReferenceType.ORDER, event.referenceId(), null, event.buyerId(),
                List.of(), null, null, event.orderNumber(), null, event.paymentStatus(),
                event.cancellationScope(), null, null, event.occurredAt()
        );
    }

    private EventType resolveOrderEventType(OrderNotificationEvent event) {
        if ("PAYMENT_PAID".equals(event.eventType()) && "PAID".equals(event.paymentStatus())) {
            return EventType.PAYMENT_PAID;
        }
        if ("PAYMENT_FAILED".equals(event.eventType())
                && ("FAILED".equals(event.paymentStatus()) || "EXPIRED".equals(event.paymentStatus()))) {
            return EventType.PAYMENT_FAILED;
        }
        if ("ORDER_CANCELLED".equals(event.eventType()) && "FULL".equals(event.cancellationScope())) {
            return EventType.ORDER_CANCELLED;
        }
        throw new IllegalArgumentException("Invalid event type, payment status or cancellation scope");
    }

    void validateRecord(ConsumerRecord<String, String> record, UUID aggregateId) {
        if (aggregateId == null) {
            throw new IllegalArgumentException("Event aggregate ID is required");
        }
        if (!aggregateId.toString().equals(record.key())) {
            throw new IllegalArgumentException("Kafka key must match the event aggregate ID");
        }
        messageTimestamp(record);
    }

    // Stable only for the same Kafka record, not producer/DLT republication or topic recreation.
    private UUID eventId(ConsumerRecord<String, String> record) {
        String identity = "notification:" + record.topic() + ":"
                + record.partition() + ":" + record.offset();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    // No occurredAt in the wire DTO: this is the Kafka timestamp, not a domain event timestamp.
    private Instant messageTimestamp(ConsumerRecord<String, String> record) {
        if (record.timestamp() < 0) {
            throw new IllegalArgumentException("Kafka message timestamp is missing");
        }
        return Instant.ofEpochMilli(record.timestamp());
    }
}
