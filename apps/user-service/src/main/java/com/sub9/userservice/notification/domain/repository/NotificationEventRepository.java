package com.sub9.userservice.notification.domain.repository;

import com.sub9.userservice.notification.domain.model.NotificationEvent;

import java.util.Optional;
import java.util.UUID;

public interface NotificationEventRepository {

    Optional<NotificationEvent> findById(UUID eventId);

    boolean saveNew(NotificationEvent event);

    void save(NotificationEvent event);
}
