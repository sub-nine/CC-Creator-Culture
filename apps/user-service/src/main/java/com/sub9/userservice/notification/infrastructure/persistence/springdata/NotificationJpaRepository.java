package com.sub9.userservice.notification.infrastructure.persistence.springdata;
import com.sub9.userservice.notification.domain.entity.NotificationJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationJpaRepository
        extends JpaRepository<NotificationJpaEntity, UUID> {

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    Page<NotificationJpaEntity> findAllByUserId(UUID userId, Pageable pageable);

    Optional<NotificationJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update NotificationJpaEntity n
               set n.read = true,
                   n.readAt = :readAt
             where n.userId = :userId
               and n.read = false
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
