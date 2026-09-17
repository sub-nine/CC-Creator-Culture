package com.sub9.productservice.category.application.command.port.out;

import com.sub9.common.kafka.event.CategoryCreatedEvent;
import com.sub9.common.kafka.event.HashtagCreatedEvent;

public interface OutboxRepository {
    void record(HashtagCreatedEvent event);

    void record(CategoryCreatedEvent event);
}
