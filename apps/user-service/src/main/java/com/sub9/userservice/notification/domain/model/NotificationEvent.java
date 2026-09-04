package com.sub9.userservice.notification.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;


@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationEvent {

    private final UUID eventId;
    private final EventType eventType;
    private final SourceService sourceService;
    private final ReferenceType referenceType;
    private final UUID referenceId;

    private EventProcessingStatus status;
    private int retryCount;
    private String errorMessage;

    private final Instant receivedAt;
    private Instant processedAt;

    public static NotificationEvent received(  //새 이벤트 생성
            UUID eventId,
            EventType eventType,
            SourceService sourceService,
            ReferenceType referenceType,
            UUID referenceId
    ) {
        return new NotificationEvent(
                eventId,
                eventType,
                sourceService,
                referenceType,
                referenceId,
                EventProcessingStatus.RECEIVED,
                0,
                null,
                Instant.now(),
                null
        );
    }

    public static NotificationEvent reconstitute( //이벤트 복원
            UUID eventId,
            EventType eventType,
            SourceService sourceService,
            ReferenceType referenceType,
            UUID referenceId,
            EventProcessingStatus status,
            int retryCount,
            String errorMessage,
            Instant receivedAt,
            Instant processedAt
    ) {
        return new NotificationEvent(
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

    public void markProcessing() {
        this.status = EventProcessingStatus.PROCESSING;
        this.errorMessage = null;
    }

    public void markCompleted() {
        this.status = EventProcessingStatus.COMPLETED;
        this.processedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markFailed(String message) {
        this.status = EventProcessingStatus.FAILED;
        this.retryCount++;
        this.errorMessage = message;
    }


}