package com.sub9.userservice.notification.application.dto;

import com.sub9.userservice.notification.domain.model.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationView(
        UUID id,
        String type,
        String title,
        String content,
        String referenceType,
        UUID referenceId,
        boolean read,
        Instant createdAt,
        Instant readAt
) {

    public static NotificationView from(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getContent(),
                notification.getReferenceType().name(),
                notification.getReferenceId(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
