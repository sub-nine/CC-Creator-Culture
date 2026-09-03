package com.sub9.userservice.notification.infrastructure.persistence.adapter;

import com.sub9.userservice.notification.domain.model.SlackDelivery;
import com.sub9.userservice.notification.domain.model.SlackDeliveryStatus;
import com.sub9.userservice.notification.domain.repository.SlackDeliveryRepository;
import com.sub9.userservice.notification.domain.entity.SlackDeliveryJpaEntity;
import com.sub9.userservice.notification.infrastructure.persistence.springdata.SlackDeliveryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SlackDeliveryRepositoryAdapter implements SlackDeliveryRepository {

    private final SlackDeliveryJpaRepository slackdeliveryjpaRepository;

    @Override
    public boolean existsByEventIdAndDestination(
            UUID eventId,
            String destination) {
        return slackdeliveryjpaRepository.existsByEventIdAndDestination(eventId, destination);
    }

    @Override
    public void save(SlackDelivery delivery) {
        slackdeliveryjpaRepository.save(SlackDeliveryJpaEntity.fromDomain(delivery));
    }

    @Override
    public List<SlackDelivery> findPendingReady(Instant now, int limit) {
        return slackdeliveryjpaRepository.findReady(
                        SlackDeliveryStatus.PENDING,
                        now,
                        PageRequest.of(0, limit)
                ).stream()
                .map(SlackDeliveryJpaEntity::toDomain)
                .toList();
    }
}
