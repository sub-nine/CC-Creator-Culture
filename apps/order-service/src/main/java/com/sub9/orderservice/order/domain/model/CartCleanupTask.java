package com.sub9.orderservice.order.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_order_cart_cleanup_tasks", indexes =
        @Index(name = "idx_cart_cleanup_due", columnList = "next_attempt_at, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartCleanupTask {
    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp")
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "timestamp")
    private Instant nextAttemptAt;

    public CartCleanupTask(UUID id, UUID orderId, String payload, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.orderId = Objects.requireNonNull(orderId);
        this.payload = Objects.requireNonNull(payload);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.nextAttemptAt = createdAt;
    }
}
