package com.sub9.common.kafka.event;

import java.util.List;
import java.util.UUID;

public record ProductCreatedEvent(
        UUID productId,
        UUID creatorId,
        String name,
        String content,
        List<String> hashTags
) {
}
