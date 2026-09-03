package com.sub9.userservice.notification.infrastructure.persistence.springdata;

import com.sub9.userservice.notification.domain.model.SlackDeliveryStatus;
import com.sub9.userservice.notification.domain.entity.SlackDeliveryJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SlackDeliveryJpaRepository
        extends JpaRepository<SlackDeliveryJpaEntity, UUID> {

    boolean existsByEventIdAndDestination(UUID eventId, String destination);

    @Query("""
            select delivery from SlackDeliveryJpaEntity delivery
            where delivery.status = :status
              and (delivery.nextRetryAt is null or delivery.nextRetryAt <= :now)
            order by delivery.requestedAt asc
            """)
    List<SlackDeliveryJpaEntity> findReady(
            @Param("status") SlackDeliveryStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}