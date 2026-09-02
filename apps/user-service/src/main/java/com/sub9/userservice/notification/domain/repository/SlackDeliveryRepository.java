package com.sub9.userservice.notification.domain.repository;

import com.sub9.userservice.notification.domain.model.SlackDelivery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SlackDeliveryRepository {

    boolean existsByEventIdAndDestination(UUID eventId, String destination);

    void save(SlackDelivery delivery);

    List<SlackDelivery> findPendingReady(Instant now, int limit);
}
