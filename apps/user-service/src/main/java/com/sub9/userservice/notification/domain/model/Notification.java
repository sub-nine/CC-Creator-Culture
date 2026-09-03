package com.sub9.userservice.notification.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;


@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Notification {

    private final UUID id;
    private final UUID eventId;
    private final UUID userId;
    private final EventType type;
    private final String title;
    private final String content;
    private final ReferenceType referenceType;
    private final UUID referenceId;

    private boolean read;

    private final Instant createdAt;
    private Instant readAt;

    public static Notification create(
            UUID eventId,
            UUID userId,
            EventType type,
            String title,
            String content,
            ReferenceType referenceType,
            UUID referenceId
    ) {
        return new Notification(
                UuidV7Generator.generate(),
                eventId,
                userId,
                type,
                title,
                content,
                referenceType,
                referenceId,
                false,
                Instant.now(),
                null
        );
    }


    public static Notification reconstitute(
            UUID id,
            UUID eventId,
            UUID userId,
            EventType type,
            String title,
            String content,
            ReferenceType referenceType,
            UUID referenceId,
            boolean read,
            Instant createdAt,
            Instant readAt
    ) {
        return new Notification(
                id,
                eventId,
                userId,
                type,
                title,
                content,
                referenceType,
                referenceId,
                read,
                createdAt,
                readAt
        );
    }


    public void markRead() {
        if (!read) {
            this.read = true;
            this.readAt = Instant.now();
        }
    }
}

