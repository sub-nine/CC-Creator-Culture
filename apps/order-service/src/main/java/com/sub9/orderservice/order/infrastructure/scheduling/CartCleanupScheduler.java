package com.sub9.orderservice.order.infrastructure.scheduling;

import com.sub9.orderservice.order.application.service.CartCleanupTransactionService;
import com.sub9.orderservice.order.domain.model.CartCleanupTask;
import com.sub9.orderservice.order.domain.repository.CartCleanupTaskRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order.cart-cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CartCleanupScheduler {
    private final CartCleanupTaskRepository tasks;
    private final CartCleanupTransactionService transactions;
    private final Clock clock;
    private final CartCleanupMetrics metrics;

    @Scheduled(fixedDelayString = "${order.cart-cleanup.interval-ms:5000}", initialDelay = 5_000L)
    public void cleanup() {
        long started = System.nanoTime();
        try {
            processDue();
        } finally {
            metrics.execution(System.nanoTime() - started);
        }
    }

    private void processDue() {
        Instant now = clock.instant();
        for (CartCleanupTask task : tasks.findDue(now, 100)) {
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
    }
}
