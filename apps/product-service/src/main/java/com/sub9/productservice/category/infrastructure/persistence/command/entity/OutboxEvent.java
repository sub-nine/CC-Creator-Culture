package com.sub9.productservice.category.infrastructure.persistence.command.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_category_outbox_events")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private OutboxEventType type;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void markPublished() {
        if (this.status != OutboxStatus.PROCESSING) {
            throw new IllegalArgumentException("적절하지 않은 상태전이입니다.");
        }
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void markProcessing() {
        if (this.status != OutboxStatus.PENDING) {
            throw new IllegalArgumentException("적절하지 않은 상태전이입니다.");
        }
        this.status = OutboxStatus.PROCESSING;
    }

    // 재시도 한도를 넘었으면 데드레터(FAILED)로, 아니면 PENDING으로 되돌려 다음 주기에 릴레이가 다시 시도하도록 함
    public void recordFailure(int maxAttempt, String errorMessage) {
        this.attemptCount++;
        this.claimedAt = null;
        this.errorMessage = errorMessage;
        this.status = (this.attemptCount >= maxAttempt) ? OutboxStatus.FAILED : OutboxStatus.PENDING;
    }

    public static OutboxEvent pending(OutboxEventType type, String payload) {
        return OutboxEvent.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .type(type)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .createdAt(Instant.now())
                .build();
    }

    @Builder
    private OutboxEvent(
            UUID id, OutboxEventType type, String payload, OutboxStatus status, int attemptCount, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.payload = payload;
        this.status = status;
        this.attemptCount = attemptCount;
        this.createdAt = createdAt;
    }
}
