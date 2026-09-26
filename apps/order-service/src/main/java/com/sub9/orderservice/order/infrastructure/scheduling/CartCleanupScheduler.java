package com.sub9.orderservice.order.infrastructure.scheduling;

import com.sub9.orderservice.order.application.service.CartCleanupTransactionService;
import com.sub9.orderservice.order.domain.model.CartCleanupTask;
import com.sub9.orderservice.order.domain.repository.CartCleanupTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.cart-cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CartCleanupScheduler {
    private final CartCleanupTaskRepository tasks;
    private final CartCleanupTransactionService transactions;
    private final Clock clock;
    private final CartCleanupMetrics metrics;
    private final int batchSize;
    private final int maxConsecutiveBatches;

    public CartCleanupScheduler(
            CartCleanupTaskRepository tasks,
            CartCleanupTransactionService transactions,
            Clock clock,
            CartCleanupMetrics metrics,
            @Value("${order.cart-cleanup.batch-size:100}") int batchSize,
            @Value("${order.cart-cleanup.max-consecutive-batches:10}") int maxConsecutiveBatches) {
        this.tasks = tasks;
        this.transactions = transactions;
        this.clock = clock;
        this.metrics = metrics;
        this.batchSize = batchSize;
        this.maxConsecutiveBatches = maxConsecutiveBatches;
    }

    @Scheduled(fixedDelayString = "${order.cart-cleanup.interval-ms:500}", initialDelay = 5_000L)
    public void cleanup() {
        long started = System.nanoTime();
        try {
            processDue();
        } finally {
            // 연속 배치를 포함한 이번 스케줄 실행 전체 시간을 기록한다.
            metrics.execution(System.nanoTime() - started);
        }
    }

    private void processDue() {
        Instant now = clock.instant();
        // 스케줄러 스레드를 주문 만료와 공유하므로, 적체가 길어도 연속 배치 상한에서 양보한다.
        for (int batch = 0; batch < maxConsecutiveBatches; batch++) {
            List<CartCleanupTask> due = tasks.findDue(now, batchSize);
            for (CartCleanupTask task : due) {
                try {
                    // 프록시의 트랜잭션 커밋이 끝난 뒤에만 실제 삭제 성공을 집계한다.
                    if (transactions.process(task.getId(), now)) {
                        metrics.succeeded(task.getCreatedAt());
                    }
                } catch (RuntimeException exception) {
                    metrics.failed();
                    log.error("장바구니 정리 실패: orderId={}", task.getOrderId(), exception);
                    try {
                        transactions.postpone(task.getId(), clock.instant().plusSeconds(60));
                    } catch (RuntimeException retryException) {
                        log.error("장바구니 정리 재시도 시각 저장 실패: orderId={}", task.getOrderId(), retryException);
                    }
                }
            }
            if (due.size() < batchSize) {
                return;
            }
        }
    }
}
