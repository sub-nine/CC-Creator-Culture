package com.sub9.orderservice.order.infrastructure.scheduling;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import com.sub9.orderservice.order.application.service.OrderExpirationTransactionService;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("주문 만료 스케줄러")
class OrderExpirationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:10:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderExpirationTransactionService transactionService;

    @Mock
    private StockPort stockPort;

    private OrderExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OrderExpirationScheduler(
                orderRepository,
                transactionService,
                stockPort,
                Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    @Test
    @DisplayName("오래된 후보를 100건까지 조회하고 주문별 커밋 뒤 재고를 복구한다")
    void when_expiration_runs_each_committed_order_stock_is_restored() {
        UUID firstOrderId = uuid(1);
        UUID secondOrderId = uuid(2);
        StockRestoreCommand first = command(firstOrderId, 101);
        StockRestoreCommand second = command(secondOrderId, 102);
        when(orderRepository.findExpiredPendingOrderIds(NOW, 100))
                .thenReturn(List.of(firstOrderId, secondOrderId));
        when(transactionService.expire(firstOrderId, NOW)).thenReturn(Optional.of(first));
        when(transactionService.expire(secondOrderId, NOW)).thenReturn(Optional.of(second));

        scheduler.expireOrders();

        InOrder ordered = inOrder(transactionService, stockPort);
        ordered.verify(transactionService).expire(firstOrderId, NOW);
        ordered.verify(stockPort).restore(first.orderId(), first.items(), first.reason());
        ordered.verify(transactionService).expire(secondOrderId, NOW);
        ordered.verify(stockPort).restore(second.orderId(), second.items(), second.reason());
    }

    @Test
    @DisplayName("한 주문의 로컬 만료 처리가 실패해도 다음 후보를 계속 처리한다")
    void when_local_expiration_fails_next_candidate_is_processed() {
        UUID failedOrderId = uuid(3);
        UUID nextOrderId = uuid(4);
        StockRestoreCommand next = command(nextOrderId, 104);
        when(orderRepository.findExpiredPendingOrderIds(NOW, 100))
                .thenReturn(List.of(failedOrderId, nextOrderId));
        when(transactionService.expire(failedOrderId, NOW))
                .thenThrow(new IllegalStateException("쿠폰 복구 실패"));
        when(transactionService.expire(nextOrderId, NOW)).thenReturn(Optional.of(next));

        scheduler.expireOrders();

        verify(transactionService).expire(nextOrderId, NOW);
        verify(stockPort).restore(next.orderId(), next.items(), next.reason());
    }

    @Test
    @DisplayName("한 주문의 재고 복구가 실패해도 다음 후보를 계속 처리한다")
    void when_stock_restore_fails_next_candidate_is_processed() {
        UUID failedOrderId = uuid(5);
        UUID nextOrderId = uuid(6);
        StockRestoreCommand failed = command(failedOrderId, 105);
        StockRestoreCommand next = command(nextOrderId, 106);
        when(orderRepository.findExpiredPendingOrderIds(NOW, 100))
                .thenReturn(List.of(failedOrderId, nextOrderId));
        when(transactionService.expire(failedOrderId, NOW)).thenReturn(Optional.of(failed));
        when(transactionService.expire(nextOrderId, NOW)).thenReturn(Optional.of(next));
        doThrow(new IllegalStateException("재고 복구 실패"))
                .when(stockPort)
                .restore(failed.orderId(), failed.items(), failed.reason());

        scheduler.expireOrders();

        verify(transactionService).expire(nextOrderId, NOW);
        verify(stockPort).restore(next.orderId(), next.items(), next.reason());
    }

    private static StockRestoreCommand command(UUID orderId, long skuSequence) {
        return new StockRestoreCommand(
                orderId,
                List.of(new StockItem(uuid(skuSequence), 2)),
                RestoreReason.ORDER_EXPIRED);
    }

    private static UUID uuid(long sequence) {
        return UUID.fromString("0198f2a0-76c0-7000-8000-%012x".formatted(sequence));
    }
}
