package com.sub9.productservice.category.infrastructure.messaging.scheduler;

import com.sub9.productservice.category.infrastructure.messaging.publisher.OutboxEventPublisher;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEventType;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxStatus;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.OutboxEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelay 단위 테스트")
class OutboxRelayTest {

    @Mock
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Mock
    private OutboxEventPublisher hashtagCreatedPublisher;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        outboxRelay = new OutboxRelay(outboxEventJpaRepository, List.of(hashtagCreatedPublisher));
    }

    @Test
    @DisplayName("PENDING 이벤트 발행에 성공하면 PUBLISHED로 전이하고 저장한다")
    void publishPending_success_marksPublished() {
        OutboxEvent event = OutboxEvent.pending(OutboxEventType.HASHTAG_CREATED, "payload");

        when(outboxEventJpaRepository.findByStatusOrderByCreatedAt(eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of(event));
        when(hashtagCreatedPublisher.supports(OutboxEventType.HASHTAG_CREATED)).thenReturn(true);
        when(hashtagCreatedPublisher.publish(event)).thenReturn(CompletableFuture.completedFuture(null));

        outboxRelay.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxEventJpaRepository).save(event);
    }

    @Test
    @DisplayName("매칭되는 publisher가 없으면 즉시 FAILED로 처리한다")
    void publishPending_noMatchingPublisher_marksFailedImmediately() {
        OutboxEvent event = OutboxEvent.pending(OutboxEventType.HASHTAG_CREATED, "payload");

        when(outboxEventJpaRepository.findByStatusOrderByCreatedAt(eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of(event));
        when(hashtagCreatedPublisher.supports(OutboxEventType.HASHTAG_CREATED)).thenReturn(false);

        outboxRelay.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getErrorMessage()).contains("event_type에 맞는 publisher가 없음");
        verify(hashtagCreatedPublisher, never()).publish(any());
        verify(outboxEventJpaRepository).save(event);
    }

    @Test
    @DisplayName("발행 중 동기 예외가 발생하면 재시도 한도 미만이라 PENDING으로 되돌린다")
    void publishPending_synchronousException_belowRetryLimit_staysPending() {
        OutboxEvent event = OutboxEvent.pending(OutboxEventType.HASHTAG_CREATED, "payload");

        when(outboxEventJpaRepository.findByStatusOrderByCreatedAt(eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of(event));
        when(hashtagCreatedPublisher.supports(OutboxEventType.HASHTAG_CREATED)).thenReturn(true);
        when(hashtagCreatedPublisher.publish(event)).thenThrow(new RuntimeException("카프카 연결 실패"));

        outboxRelay.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getErrorMessage()).isEqualTo("카프카 연결 실패");
        verify(outboxEventJpaRepository).save(event);
    }

    @Test
    @DisplayName("비동기 발행 실패가 재시도 한도(5회)에 도달하면 FAILED로 전환한다")
    void publishPending_asyncFailure_reachesRetryLimit_marksFailed() {
        OutboxEvent event = OutboxEvent.pending(OutboxEventType.HASHTAG_CREATED, "payload");
        // 이전에 4번 실패해서 attemptCount를 4로 만들어둠(재시도 한도 5 미만이라 아직 PENDING)
        for (int i = 0; i < 4; i++) {
            event.recordFailure(5, "이전 실패");
        }
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);

        when(outboxEventJpaRepository.findByStatusOrderByCreatedAt(eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of(event));
        when(hashtagCreatedPublisher.supports(OutboxEventType.HASHTAG_CREATED)).thenReturn(true);
        when(hashtagCreatedPublisher.publish(event))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("카프카 연결 실패")));

        outboxRelay.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttemptCount()).isEqualTo(5);
        verify(outboxEventJpaRepository).save(event);
    }

    @Test
    @DisplayName("여러 publisher 중 이벤트 타입을 지원하는 publisher에게만 발행을 위임한다")
    void publishPending_multiplePublishers_delegatesToSupportingPublisherOnly() {
        OutboxEventPublisher otherPublisher = mock(OutboxEventPublisher.class);
        outboxRelay = new OutboxRelay(outboxEventJpaRepository, List.of(otherPublisher, hashtagCreatedPublisher));

        OutboxEvent event = OutboxEvent.pending(OutboxEventType.HASHTAG_CREATED, "payload");

        when(outboxEventJpaRepository.findByStatusOrderByCreatedAt(eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of(event));
        when(otherPublisher.supports(OutboxEventType.HASHTAG_CREATED)).thenReturn(false);
        when(hashtagCreatedPublisher.supports(OutboxEventType.HASHTAG_CREATED)).thenReturn(true);
        when(hashtagCreatedPublisher.publish(event)).thenReturn(CompletableFuture.completedFuture(null));

        outboxRelay.publishPending();

        verify(otherPublisher, never()).publish(any());
        verify(hashtagCreatedPublisher).publish(event);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }
}
