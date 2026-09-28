package com.sub9.orderservice.order.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_order_event_outbox", indexes =
        @Index(name = "idx_order_event_outbox_due", columnList = "next_attempt_at, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEventOutbox {
    @Id
    private UUID id;

    @Column(nullable = false, updatable = false, length = 100)
    private String topic;

    @Column(name = "message_key", nullable = false, updatable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant nextAttemptAt;

    public OrderEventOutbox(UUID id, String topic, String messageKey, String payload, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.topic = Objects.requireNonNull(topic);
        this.messageKey = Objects.requireNonNull(messageKey);
        this.payload = Objects.requireNonNull(payload);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.nextAttemptAt = createdAt;
    }
}
