package com.sub9.productservice.category.infrastructure.messaging.publisher;

import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEventType;

import java.util.concurrent.CompletableFuture;

public interface OutboxEventPublisher {

    boolean supports(OutboxEventType type);

    CompletableFuture<Void> publish(OutboxEvent event);
}
