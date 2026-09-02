package com.sub9.userservice.notification.infrastructure.persistence.adapter;

import com.sub9.userservice.notification.domain.model.NotificationEvent;
import com.sub9.userservice.notification.domain.repository.NotificationEventRepository;
import com.sub9.userservice.notification.infrastructure.persistence.entity.NotificationEventJpaEntity;
import com.sub9.userservice.notification.infrastructure.persistence.springdata.NotificationEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Domain 저장소 인터페이스를 Spring Data JPA로 구현하는 Infrastructure Adapter입니다. */
@Repository
@RequiredArgsConstructor
public class NotificationEventRepositoryAdapter implements NotificationEventRepository {

    private final NotificationEventJpaRepository notificationeventjpaRepository;

    @Override
    public Optional<NotificationEvent> findById(UUID eventId) {
        return notificationeventjpaRepository
                .findById(eventId)
                .map(NotificationEventJpaEntity::toDomain);
    }

    @Override
    public boolean saveNew(NotificationEvent event) {
        int inserted = notificationeventjpaRepository.insertIfAbsent(
                event.getEventId(),
                event.getEventType().name(),
                event.getSourceService().name(),
                event.getReferenceType().name(),
                event.getReferenceId(),
                event.getStatus().name(),
                event.getRetryCount(),
                event.getErrorMessage(),
                event.getReceivedAt(),
                event.getProcessedAt()
        );
        return inserted == 1;
    }

    @Override
    public void save(NotificationEvent event) {
        notificationeventjpaRepository.save(NotificationEventJpaEntity.fromDomain(event));
    }
}
