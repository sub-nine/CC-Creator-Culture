package com.sub9.userservice.notification.infrastructure.messaging.kafka;

import com.sub9.userservice.notification.application.service.NotificationEventCoordinator;
import com.sub9.userservice.notification.domain.model.EventType;
import com.sub9.userservice.notification.domain.model.ReferenceType;
import com.sub9.userservice.notification.domain.model.SourceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class KafkaNotificationEventConsumer {

    private static final Set<EventType> PRODUCT_EVENT_TYPES = Set.of(
            EventType.PRODUCT_CREATED,
            EventType.PRODUCT_LOW_STOCK,
            EventType.PRODUCT_SOLD_OUT,
            EventType.PRODUCT_RESTOCKED

    );
    private static final Set<EventType> ORDER_EVENT_TYPES = Set.of(
            EventType.ORDER_CREATED,
            EventType.ORDER_CANCELLED,
            EventType.PAYMENT_PAID,
            EventType.PAYMENT_FAILED
    );

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final NotificationEventCoordinator coordinator;

    @KafkaListener(topics = "${app.kafka.topics.product-events}")
    public void consumeProductEvent(String payload) {
        consume(
                payload,
                SourceService.PRODUCT_SERVICE,
                ReferenceType.PRODUCT,
                PRODUCT_EVENT_TYPES
        );
    }

    @KafkaListener(topics = "${app.kafka.topics.order-events}")
    public void consumeOrderEvent(String payload) {
        consume(
                payload,
                SourceService.ORDER_SERVICE,
                ReferenceType.ORDER,
                ORDER_EVENT_TYPES
        );
    }

    private void consume(
            String payload,
            SourceService expectedSource,
            ReferenceType expectedReferenceType,
            Set<EventType> allowedEventTypes
    ) {
        try {
            KafkaDomainEventPayload event = objectMapper.readValue(
                    payload,
                    KafkaDomainEventPayload.class
            );

            validateTopicContract(
                    event,
                    expectedSource,
                    expectedReferenceType,
                    allowedEventTypes
            );
            coordinator.handle(event.toCommand());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid event JSON", exception);
        }
    }

    private void validateTopicContract(
            KafkaDomainEventPayload event,
            SourceService expectedSource,
            ReferenceType expectedReferenceType,
            Set<EventType> allowedEventTypes
    ) {
        if (event.sourceService() != expectedSource) {
            throw new IllegalArgumentException(
                    "Invalid sourceService for topic: " + event.sourceService()
            );
        }
        if (event.referenceType() != expectedReferenceType) {
            throw new IllegalArgumentException(
                    "Invalid referenceType for topic: " + event.referenceType()
            );
        }
        if (!allowedEventTypes.contains(event.eventType())) {
            throw new IllegalArgumentException(
                    "Event type is not allowed on this topic: " + event.eventType()
            );
        }
    }
}
