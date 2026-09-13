package com.sub9.common.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record ProductDeletedEvent(UUID eventId, UUID productId, Instant occurredAt) {}
