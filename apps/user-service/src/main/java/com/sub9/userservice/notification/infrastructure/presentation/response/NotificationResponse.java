package com.sub9.userservice.notification.infrastructure.presentation.response;

import com.sub9.userservice.notification.application.dto.NotificationView;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
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

    public static NotificationResponse from(NotificationView view) {
        return new NotificationResponse(
                view.id(), view.type(), view.title(), view.content(),
                view.referenceType(), view.referenceId(), view.read(),
                view.createdAt(), view.readAt()
        );
    }
}