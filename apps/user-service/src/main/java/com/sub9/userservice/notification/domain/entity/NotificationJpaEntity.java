package com.sub9.userservice.notification.domain.entity;

import com.sub9.userservice.notification.domain.model.EventType;
import com.sub9.userservice.notification.domain.model.Notification;
import com.sub9.userservice.notification.domain.model.ReferenceType;
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
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationJpaEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EventType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private ReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    private NotificationJpaEntity(Notification domain) {
        this.id = domain.getId();
        this.eventId = domain.getEventId();
        this.userId = domain.getUserId();
        this.type = domain.getType();
        this.title = domain.getTitle();
        this.content = domain.getContent();
        this.referenceType = domain.getReferenceType();
        this.referenceId = domain.getReferenceId();
        this.read = domain.isRead();
        this.createdAt = domain.getCreatedAt();
        this.readAt = domain.getReadAt();
    }

    public static NotificationJpaEntity fromDomain(Notification domain) {
        return new NotificationJpaEntity(domain);
    }

    public Notification toDomain() {
        return Notification.reconstitute(
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
}
