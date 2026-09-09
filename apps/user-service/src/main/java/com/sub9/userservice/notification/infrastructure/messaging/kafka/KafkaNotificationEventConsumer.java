package com.sub9.userservice.notification.infrastructure.messaging.kafka;

import com.sub9.common.kafka.event.OrderPaidEvent;
import com.sub9.common.kafka.event.ProductCreatedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.userservice.notification.application.port.OrderNotificationLookup;
import com.sub9.userservice.notification.application.service.NotificationEventCoordinator;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class KafkaNotificationEventConsumer {
    private final ObjectMapper objectMapper;
    private final NotificationEventMapper mapper;
    private final NotificationEventCoordinator coordinator;
    private final ObjectProvider<OrderNotificationLookup> orderLookupProvider;

    @KafkaListener(topics = KafkaTopics.PRODUCT_CREATED)
    public void consumeProductEvent(ConsumerRecord<String, String> record) {
        ProductCreatedEvent event = read(record, ProductCreatedEvent.class);
        coordinator.handle(mapper.fromProductCreated(event, record));
    }

    @KafkaListener(topics = KafkaTopics.ORDER_PAID)
    public void consumeOrderEvent(ConsumerRecord<String, String> record) {
        OrderPaidEvent event = read(record, OrderPaidEvent.class);
        mapper.validateRecord(record, event.orderId());
        // The wire event has no buyer information. Never fabricate a recipient.
        OrderNotificationLookup lookup = orderLookupProvider.getIfAvailable();
        if (lookup == null) {
            throw new IllegalStateException("OrderNotificationLookup adapter is not configured");
        }
        var order = lookup.findByOrderId(event.orderId());
        coordinator.handle(mapper.fromOrderPaid(event, order, record));
    }

    private <T> T read(ConsumerRecord<String, String> record, Class<T> type) {
        if (record.value() == null || record.value().isBlank()) {
            throw new IllegalArgumentException("Event payload is empty");
        }
        try {
            T event = objectMapper.readValue(record.value(), type);
            if (event == null) {
                throw new IllegalArgumentException("Event payload is null");
            }
            return event;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid event JSON: " + type.getSimpleName(), exception);
        }
    }
}
