package com.sub9.userservice.notification.infrastructure.persistence.entity;

import com.sub9.userservice.notification.domain.model.SlackDelivery;
import com.sub9.userservice.notification.domain.model.SlackDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "p_notification_slack")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlackDeliveryJpaEntity {

    @Id
    @Column(name = "slack_delivery_id", nullable = false, updatable = false)
    private UUID slackDeliveryId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, length = 200)
    private String destination;

    @Column(name = "slack_message", nullable = false, columnDefinition = "TEXT")
    private String slackMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlackDeliveryStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private SlackDeliveryJpaEntity(SlackDelivery domain) {
        this.slackDeliveryId = domain.getSlackDeliveryId();
        this.eventId = domain.getEventId();
        this.destination = domain.getDestination();
        this.slackMessage = domain.getSlackMessage();
        this.status = domain.getStatus();
        this.attemptCount = domain.getAttemptCount();
        this.retryCount = domain.getRetryCount();
        this.errorCode = domain.getErrorCode();
        this.errorMessage = domain.getErrorMessage();
        this.requestedAt = domain.getRequestedAt();
        this.nextRetryAt = domain.getNextRetryAt();
        this.sentAt = domain.getSentAt();
        this.updatedAt = domain.getUpdatedAt();
    }

    public static SlackDeliveryJpaEntity fromDomain(SlackDelivery domain) {
        return new SlackDeliveryJpaEntity(domain);
    }

    public SlackDelivery toDomain() {
        return SlackDelivery.reconstitute(
                slackDeliveryId,
                eventId,
                destination,
                slackMessage,
                status,
                attemptCount,
                retryCount,
                errorCode,
                errorMessage,
                requestedAt,
                nextRetryAt,
                sentAt,
                updatedAt
        );
    }
}
