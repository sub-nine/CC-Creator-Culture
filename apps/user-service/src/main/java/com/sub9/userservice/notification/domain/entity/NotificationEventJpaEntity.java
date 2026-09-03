package com.sub9.userservice.notification.domain.entity;

import com.sub9.userservice.notification.domain.model.EventProcessingStatus;
import com.sub9.userservice.notification.domain.model.EventType;
import com.sub9.userservice.notification.domain.model.NotificationEvent;
import com.sub9.userservice.notification.domain.model.ReferenceType;
import com.sub9.userservice.notification.domain.model.SourceService;
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
@Table(name = "notification_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_service", nullable = false, length = 50)
    private SourceService sourceService;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private ReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventProcessingStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    private NotificationEventJpaEntity(NotificationEvent domain) {
        this.eventId = domain.getEventId();
        this.eventType = domain.getEventType();
        this.sourceService = domain.getSourceService();
        this.referenceType = domain.getReferenceType();
        this.referenceId = domain.getReferenceId();
        this.status = domain.getStatus();
        this.retryCount = domain.getRetryCount();
        this.errorMessage = domain.getErrorMessage();
        this.receivedAt = domain.getReceivedAt();
        this.processedAt = domain.getProcessedAt();
    }

    public static NotificationEventJpaEntity fromDomain(NotificationEvent domain) {
        return new NotificationEventJpaEntity(domain);
    }

    public NotificationEvent toDomain() {
        return NotificationEvent.reconstitute(
                eventId,
                eventType,
                sourceService,
                referenceType,
                referenceId,
                status,
                retryCount,
                errorMessage,
                receivedAt,
                processedAt
        );
    }
}
