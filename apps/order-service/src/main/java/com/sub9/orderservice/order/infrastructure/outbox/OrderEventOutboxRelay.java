package com.sub9.orderservice.order.infrastructure.outbox;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.event-outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventOutboxRelay {
    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OrderEventOutboxJpaRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration retryDelay;
    private final OrderEventOutboxMetrics metrics;

    public OrderEventOutboxRelay(OrderEventOutboxJpaRepository outbox, KafkaTemplate<String, String> kafkaTemplate,
            TransactionTemplate transactions, Clock clock,
            @Value("${order.event-outbox.retry-delay:60s}") Duration retryDelay, OrderEventOutboxMetrics metrics) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
        this.transactions = transactions;
        this.clock = clock;
        this.retryDelay = retryDelay;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${order.event-outbox.interval-ms:500}", initialDelay = 5_000L)
    public void publishDue() {
        for (OrderEventOutbox record : claim()) {
            try {
                kafkaTemplate.send(record.getTopic(), record.getMessageKey(), record.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                metrics.failed();
                // 점유할 때 미뤄 둔 다음 시도 시각이 지나면 다시 발행한다.
                log.warn("주문 이벤트 발행 실패: id={}, topic={}, key={}",
                        record.getId(), record.getTopic(), record.getMessageKey(), exception);
                continue;
            }
            metrics.succeeded();
            // 브로커 기록이 확인된 뒤에만 지운다. 삭제가 실패하면 같은 이벤트가 다시 발행될 수 있다.
            outbox.deleteById(record.getId());
        }
    }

    // 짧은 트랜잭션에서 점유하고 다음 시도를 미뤄, Kafka 전송 중에는 DB 연결과 행 잠금을 잡고 있지 않는다.
    private List<OrderEventOutbox> claim() {
        return transactions.execute(status -> {
            List<OrderEventOutbox> due = outbox.findDueForUpdate(clock.instant(), BATCH_SIZE);
            if (!due.isEmpty()) {
                outbox.postpone(due.stream().map(OrderEventOutbox::getId).toList(), clock.instant().plus(retryDelay));
            }
            return due;
        });
    }
}
