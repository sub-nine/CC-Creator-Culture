package com.sub9.userservice.notification.infrastructure.messaging.kafka;

import com.sub9.common.kafka.event.OrderPaidEvent;
import com.sub9.common.kafka.event.ProductCreatedEvent;
import com.sub9.userservice.notification.application.dto.NotificationEventCommand;
import com.sub9.userservice.notification.application.port.OrderNotificationLookup.OrderNotificationInfo;
import com.sub9.userservice.notification.domain.model.EventType;
import com.sub9.userservice.notification.domain.model.ReferenceType;
import com.sub9.userservice.notification.domain.model.SourceService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
                null, null, null, event.name(), null, null, null, null, null, null,
                messageTimestamp(record)
        );
    }

    public NotificationEventCommand fromOrderPaid(
            OrderPaidEvent event, OrderNotificationInfo order, ConsumerRecord<String, String> record
    ) {
        validateRecord(record, event.orderId());
        if (order == null) {
            throw new IllegalStateException("Order notification information was not found");
        }
        return new NotificationEventCommand(
                eventId(record), EventType.PAYMENT_PAID, SourceService.ORDER_SERVICE,
                ReferenceType.ORDER, event.orderId(), null, order.buyerId(),
                null, null, null, order.orderNumber(), null, "PAID", null, null, null,
                messageTimestamp(record)
        );
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
