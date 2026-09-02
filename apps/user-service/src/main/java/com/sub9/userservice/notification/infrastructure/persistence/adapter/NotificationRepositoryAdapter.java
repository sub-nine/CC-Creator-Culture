package com.sub9.userservice.notification.infrastructure.persistence.adapter;

import com.sub9.userservice.notification.domain.model.Notification;
import com.sub9.userservice.notification.domain.model.NotificationPage;
import com.sub9.userservice.notification.domain.repository.NotificationRepository;
import com.sub9.userservice.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import com.sub9.userservice.notification.infrastructure.persistence.springdata.NotificationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Domain의 알림 저장소 계약을 JPA 조회와 Entity 변환으로 구현합니다. */
@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository notificationjpaRepository;

    @Override
    public boolean existsByEventIdAndUserId(UUID eventId, UUID userId) {
        return notificationjpaRepository.existsByEventIdAndUserId(eventId, userId);
    }

    @Override
    public void save(Notification notification) {
        notificationjpaRepository.save(NotificationJpaEntity.fromDomain(notification));
    }

    @Override
    public NotificationPage findPageByUserId(
            UUID userId,
            int page,
            int size) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        var result = notificationjpaRepository.findAllByUserId(userId, pageable);
        return new NotificationPage(
                result.getContent().stream().map(NotificationJpaEntity::toDomain).toList(),
                result.getTotalElements()
        );
    }

    @Override
    public Optional<Notification> findByIdAndUserId(UUID notificationId, UUID userId) {
        return notificationjpaRepository.findByIdAndUserId(notificationId, userId)
                .map(NotificationJpaEntity::toDomain);
    }

    @Override
    public long countUnread(UUID userId) {
        return notificationjpaRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    public int markAllRead(UUID userId, Instant readAt) {
        return notificationjpaRepository.markAllRead(userId, readAt);
    }
}
