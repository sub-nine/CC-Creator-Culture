package com.sub9.productservice.category.infrastructure.messaging.scheduler;

import com.sub9.productservice.category.infrastructure.messaging.publisher.OutboxEventPublisher;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEventType;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxStatus;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 10;
    private static final int MAX_ATTEMPT = 5;

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    // OutboxEventPublisher 구현체 리스트 자동 주입
    private final List<OutboxEventPublisher> publishers;

    // TODO: 발행 주기는 운영 중 부하를 보고 조정
    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishPending() {
        // 주기적으로 PENDING 상태의 이벤트를 BATCH_SIZE 만큼 발행

        List<OutboxEvent> pendingEvents = outboxEventJpaRepository
                .findByStatusOrderByCreatedAt(OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE));

        for (OutboxEvent event : pendingEvents) {
            event.markProcessing();
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        OutboxEventPublisher publisher = findPublisher(event.getType());

        if (publisher == null) {
            // 매칭되는 publisher 자체가 없으면 재시도해도 소용없으니 즉시 데드레터 처리
            log.error("[OUTBOX] event_type에 맞는 publisher가 없음 - id: {}, eventType: {}", event.getId(), event.getType());
            event.recordFailure(0, "event_type에 맞는 publisher가 없음 - eventType : " + event.getType());
            outboxEventJpaRepository.save(event);
            return;
        }

        try {
            // TODO: 릴레이가 여러 인스턴스로 뜨는 경우, claim 쿼리(FOR UPDATE SKIP LOCKED) 없이는 중복 발행 가능성 존재
            publisher.publish(event)
                    .whenComplete((result, ex) -> onPublishComplete(event, ex));
        } catch (Exception e) {
            onPublishComplete(event, e);
        }
    }

    private void onPublishComplete(OutboxEvent event, Throwable ex) {
        if (ex == null) {
            event.markPublished();
        } else {
            log.warn("[OUTBOX] 발행 실패 - id: {}, eventType: {}, attempt: {}",
                    event.getId(), event.getType(), event.getAttemptCount(), ex);
            event.recordFailure(MAX_ATTEMPT, ex.getMessage());
        }

        // 새 트랜잭션 상에서 동작(리포지토리 프록시 직접 호출)
        outboxEventJpaRepository.save(event);
    }

    private OutboxEventPublisher findPublisher(OutboxEventType type) {
        return publishers.stream()
                .filter(publisher -> publisher.supports(type))
                .findFirst()
                .orElse(null);
    }

    // TODO: 처리 실패로 PROCESSING에 멈춰있는 이벤트 PENDING으로 복구
}
