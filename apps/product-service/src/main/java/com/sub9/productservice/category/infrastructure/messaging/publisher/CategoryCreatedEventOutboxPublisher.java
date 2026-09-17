package com.sub9.productservice.category.infrastructure.messaging.publisher;

import com.sub9.common.kafka.event.CategoryCreatedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryCreatedEventOutboxPublisher implements OutboxEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public boolean supports(OutboxEventType type) {
        return OutboxEventType.CATEGORY_CREATED == type;
    }

    @Override
    public CompletableFuture<Void> publish(OutboxEvent event) {
        CategoryCreatedEvent categoryCreatedEvent = jsonMapper.readValue(event.getPayload(), CategoryCreatedEvent.class);

        return kafkaTemplate
                .send(KafkaTopics.CATEGORY_CREATED, categoryCreatedEvent.categoryId().toString(), event.getPayload())
                .thenAccept(result -> {
                });
    }
}
