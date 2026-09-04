package com.sub9.userservice.notification.domain.repository;

import com.sub9.userservice.notification.domain.model.Notification;
import com.sub9.userservice.notification.domain.model.NotificationPage;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    void save(Notification notification);

    NotificationPage findPageByUserId(UUID userId, int page, int size);

    Optional<Notification> findByIdAndUserId(UUID notificationId, UUID userId);

    long countUnread(UUID userId);

    int markAllRead(UUID userId, Instant readAt);
}
