package com.sub9.userservice.notification.infrastructure.persistence.springdata;

import com.sub9.userservice.notification.infrastructure.persistence.entity.NotificationEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface NotificationEventJpaRepository
        extends JpaRepository<NotificationEventJpaEntity, UUID> {

    @Modifying
    @Query(value = """
            insert into notification_event (
                event_id, event_type, source_service, reference_type, reference_id,
                status, retry_count, error_message, received_at, processed_at
            ) values (
                :eventId, :eventType, :sourceService, :referenceType, :referenceId,
                :status, :retryCount, :errorMessage, :receivedAt, :processedAt
            )
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("sourceService") String sourceService,
            @Param("referenceType") String referenceType,
            @Param("referenceId") UUID referenceId,
            @Param("status") String status,
            @Param("retryCount") int retryCount,
            @Param("errorMessage") String errorMessage,
            @Param("receivedAt") Instant receivedAt,
            @Param("processedAt") Instant processedAt
    );
}
