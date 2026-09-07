package com.sub9.orderservice.order.infrastructure.scheduling;

import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import com.sub9.orderservice.order.application.service.OrderExpirationTransactionService;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "order.expiration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OrderExpirationScheduler {

    static final int BATCH_SIZE = 100;
    private static final long INTERVAL_MILLIS = 60_000L;

    private final OrderRepository orderRepository;
    private final OrderExpirationTransactionService transactionService;
    private final StockPort stockPort;
    private final Clock clock;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    public void expireOrders() {
        Instant now = clock.instant();
        for (UUID orderId : orderRepository.findExpiredPendingOrderIds(now, BATCH_SIZE)) {
            try {
                transactionService.expire(orderId, now).ifPresent(this::restoreStock);
            } catch (RuntimeException exception) {
                log.error("주문 만료 처리에 실패했습니다. orderId={}", orderId, exception);
            }
        }
    }

    private void restoreStock(StockRestoreCommand command) {
        stockPort.restore(command.orderId(), command.items(), command.reason());
    }
}
