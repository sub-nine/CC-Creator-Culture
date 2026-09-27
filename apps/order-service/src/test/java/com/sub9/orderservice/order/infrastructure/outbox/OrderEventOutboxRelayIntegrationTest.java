package com.sub9.orderservice.order.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.orderservice.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "order.cart-cleanup.enabled=false",
        "management.tracing.export.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("주문 이벤트 Outbox 발행기 PostgreSQL 통합 테스트")
class OrderEventOutboxRelayIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");
    private static final Duration RETRY_DELAY = Duration.ofSeconds(60);

    @MockitoBean private KafkaTemplate<String, String> kafka;
    @Autowired private OrderEventOutboxJpaRepository repository;
    @Autowired private OrderEventOutboxWriter writer;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    // Spring Data 저장소는 JDK 프록시라 실제 저장소에 위임하는 mock으로 일부 호출만 바꾼다.
    private OrderEventOutboxJpaRepository outbox;

    @BeforeEach
    void sendSucceeds() {
        reset(kafka);
        outbox = mock(OrderEventOutboxJpaRepository.class, delegatesTo(repository));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void cleanOutbox() {
        jdbc.update("delete from p_order_event_outbox");
    }

    @Test
    @DisplayName("기록한 트랜잭션이 롤백되면 Outbox 기록도 남지 않는다")
    void when_writing_transaction_rolls_back_outbox_record_is_discarded() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            writer.write("order.paid", "order-1", "{}");
            throw new IllegalStateException("결제 저장 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 기록하면 결제와 함께 커밋되지 않으므로 거부한다")
    void when_writing_outside_transaction_record_is_rejected() {
        assertThatThrownBy(() -> writer.write("order.paid", "order-1", "{}"))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("전송이 실패하면 기록을 남기고 재시도 시각이 지난 뒤 다시 발행한다")
    void when_send_fails_record_is_kept_and_republished_after_retry_delay() {
        UUID id = insert(NOW);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));

        relayAt(NOW).publishDue();
        assertThat(count()).isEqualTo(1);
        relayAt(NOW.plus(RETRY_DELAY).minusSeconds(1)).publishDue();
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());

        relayAt(NOW.plus(RETRY_DELAY)).publishDue();
        verify(kafka, times(2)).send("order.paid", id.toString(), "{}");
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("전송 성공 후 기록 삭제가 실패하면 재시도 시각에 같은 이벤트를 한 번 더 발행한다")
    void when_delete_fails_after_send_same_event_is_published_again() {
        UUID id = insert(NOW);
        doAnswer(call -> {
            throw new IllegalStateException("삭제 실패");
        }).doAnswer(call -> {
            repository.deleteById(id);
            return null;
        }).when(outbox).deleteById(id);

        assertThatThrownBy(() -> relayAt(NOW).publishDue()).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isEqualTo(1);

        relayAt(NOW.plus(RETRY_DELAY)).publishDue();

        // 중복 발행은 소비자가 멱등하게 처리한다는 전제다.
        verify(kafka, times(2)).send("order.paid", id.toString(), "{}");
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("발행기 두 개가 동시에 점유해도 같은 기록을 한 번만 발행한다")
    void when_two_relays_claim_concurrently_each_record_is_sent_once() throws Exception {
        List<UUID> ids = List.of(insert(NOW), insert(NOW), insert(NOW));
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // 첫 발행기의 점유 트랜잭션을 커밋 직전에 멈춰, 두 번째 발행기가 잠긴 행을 건너뛰는지 확인한다.
        doAnswer(call -> {
            int result = repository.postpone(call.getArgument(0), call.getArgument(1));
            if (firstClaimed.getCount() == 1) {
                firstClaimed.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(outbox).postpone(any(), any());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> relayAt(NOW).publishDue());
            assertThat(firstClaimed.await(10, TimeUnit.SECONDS)).isTrue();
            relayAt(NOW).publishDue();
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
        }

        for (UUID id : ids) {
            verify(kafka, times(1)).send("order.paid", id.toString(), "{}");
        }
        verify(kafka, never()).send(eq("order.notification"), anyString(), anyString());
        assertThat(count()).isZero();
    }

    private OrderEventOutboxRelay relayAt(Instant now) {
        return new OrderEventOutboxRelay(outbox, kafka, transactions, Clock.fixed(now, ZoneOffset.UTC), RETRY_DELAY);
    }

    private UUID insert(Instant createdAt) {
        UUID id = UUID.randomUUID();
        // 발행 키를 기록 ID로 두어 어떤 기록이 전송됐는지 검증한다.
        transactions.executeWithoutResult(status ->
                outbox.save(new OrderEventOutbox(id, "order.paid", id.toString(), "{}", createdAt)));
        return id;
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from p_order_event_outbox", Integer.class);
    }
}
