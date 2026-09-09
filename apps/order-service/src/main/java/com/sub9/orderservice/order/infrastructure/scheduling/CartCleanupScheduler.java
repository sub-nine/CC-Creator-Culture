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

    @Scheduled(fixedDelay = 5_000L, initialDelay = 5_000L)
    public void cleanup() {
        Instant now = clock.instant();
        for (CartCleanupTask task : tasks.findDue(now, 100)) {
            try {
                transactions.process(task.getId(), now);
            } catch (RuntimeException exception) {
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
