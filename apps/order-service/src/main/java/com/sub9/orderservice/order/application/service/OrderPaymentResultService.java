package com.sub9.orderservice.order.application.service;

import static com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason.PAYMENT_FAILED;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.application.port.input.PaymentResultUseCase;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.sub9.common.kafka.event.OrderPaidEvent;
import com.sub9.common.kafka.event.OrderNotificationEvent;
import com.sub9.common.identifier.UuidV7Generator;
import org.springframework.context.ApplicationEventPublisher;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderPaymentResultService implements PaymentResultUseCase {

    private final OrderRepository orderRepository;
    private final CouponUsagePort couponUsagePort;
    private final ApplicationEventPublisher eventPublisher;
    private final UuidV7Generator uuidGenerator;

    @Override
    @Transactional
    public void markPaid(UUID orderId, Instant processedAt) {
        Order order = findForUpdate(orderId);
        order.markPaid(processedAt);
        Map<UUID, Long> quantities = order.getItems().stream()
                .collect(Collectors.groupingBy(
                        item -> item.getProductId(),
                        Collectors.summingLong(item -> item.getProductSnapshot().getQuantity())));
        eventPublisher.publishEvent(new OrderPaidEvent(orderId, quantities.entrySet().stream()
                .map(entry -> new OrderPaidEvent.ProductQuantity(entry.getKey(), entry.getValue()))
                .toList()));
        publishNotification(order, "PAYMENT_PAID", "PAID", processedAt);
    }

    @Override
    @Transactional
    public StockRestoreCommand markPaymentFailed(UUID orderId, Instant processedAt) {
        Order order = findForUpdate(orderId);
        order.markPaymentFailed(processedAt);
        restoreCoupons(order);
        publishNotification(order, "PAYMENT_FAILED", "FAILED", processedAt);
        return new StockRestoreCommand(orderId, stockItems(order), PAYMENT_FAILED);
    }

    private void publishNotification(Order order, String eventType, String paymentStatus, Instant processedAt) {
        eventPublisher.publishEvent(new OrderNotificationEvent(
                uuidGenerator.generate(), eventType, "ORDER_SERVICE", "ORDER", order.getId(),
                order.getCustomerId(), order.getOrderNumber().toString(), paymentStatus, null, processedAt));
    }

    private Order findForUpdate(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private void restoreCoupons(Order order) {
        List<UUID> userCouponIds = order.getItems().stream()
                .map(item -> item.getUserCouponId())
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (!userCouponIds.isEmpty()) {
            couponUsagePort.restore(order.getId(), userCouponIds);
        }
    }

    private static List<StockItem> stockItems(Order order) {
        return order.getItems().stream()
                .map(item -> new StockItem(
                        item.getSkuId(),
                        item.getProductSnapshot().getQuantity()))
                .toList();
    }
}
