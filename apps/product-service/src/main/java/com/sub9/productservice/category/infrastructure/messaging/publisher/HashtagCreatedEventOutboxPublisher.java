package com.sub9.productservice.category.infrastructure.messaging.publisher;

import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.domain.event.HashtagCreatedEvent;
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
public class HashtagCreatedEventOutboxPublisher implements OutboxEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public boolean supports(OutboxEventType type) {
        return OutboxEventType.HASHTAG_CREATED == type;
    }

    @Override
    public CompletableFuture<Void> publish(OutboxEvent event) {
        HashtagCreatedEvent hashtagCreatedEvent = jsonMapper.readValue(event.getPayload(), HashtagCreatedEvent.class);

        return kafkaTemplate
                .send(KafkaTopics.HASHTAG_CREATED, hashtagCreatedEvent.hashtagId().toString(), event.getPayload())
                .thenAccept(result -> {
                });
    }
}
