package com.sub9.userservice.notification.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SlackDelivery {

    private final UUID slackDeliveryId;
    private final UUID eventId;
    private final String destination;
    private final String slackMessage;

    private SlackDeliveryStatus status;
    private int attemptCount;
    private int retryCount;
    private String errorCode;
    private String errorMessage;

    private final Instant requestedAt;
    private Instant nextRetryAt;
    private Instant sentAt;
    private Instant updatedAt;


    public static SlackDelivery pending(
            UUID eventId,
            String destination,
            String message
    ) {
        Instant now = Instant.now();

        return new SlackDelivery(
                UuidV7Generator.generate(),
                eventId,
                destination,
                message,
                SlackDeliveryStatus.PENDING,
                0,
                0,
                null,
                null,
                now,
                null,
                null,
                now
        );
    }


    public static SlackDelivery reconstitute(
            UUID slackDeliveryId,
            UUID eventId,
            String destination,
            String slackMessage,
            SlackDeliveryStatus status,
            int attemptCount,
            int retryCount,
            String errorCode,
            String errorMessage,
            Instant requestedAt,
            Instant nextRetryAt,
            Instant sentAt,
            Instant updatedAt
    ) {
        return new SlackDelivery(
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


    public void markSending() {
        if (attemptCount > 0) {
            retryCount++;
        }

        attemptCount++;
        this.status = SlackDeliveryStatus.SENDING;
        this.nextRetryAt = null;
        this.updatedAt = Instant.now();
    }


    public void markSent() {
        this.status = SlackDeliveryStatus.SENT;
        this.sentAt = Instant.now();
        this.updatedAt = sentAt;
        this.nextRetryAt = null;
        this.errorCode = null;
        this.errorMessage = null;
    }


    public void markSendFailed(
            String code,
            String message,
            int maxRetries,
            Instant nextRetryAt
    ) {
        this.errorCode = code;
        this.errorMessage = message;

        boolean retryLimitReached = retryCount >= maxRetries;

        this.status = retryLimitReached
                ? SlackDeliveryStatus.FAILED
                : SlackDeliveryStatus.PENDING;

        this.nextRetryAt = retryLimitReached
                ? null
                : nextRetryAt;

        this.updatedAt = Instant.now();
    }
}
