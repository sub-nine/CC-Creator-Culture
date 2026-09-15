package com.sub9.common.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record SkuDeletedEvent(
    UUID eventId, UUID skuId, Instant occurredAt
) {}
