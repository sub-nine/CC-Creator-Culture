package com.sub9.orderservice.order.application.service;

import static com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason.ORDER_EXPIRED;

import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Instant;
import com.sub9.common.kafka.event.OrderNotificationEvent;
import com.sub9.common.identifier.UuidV7Generator;
import org.springframework.context.ApplicationEventPublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderExpirationTransactionService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UuidV7Generator uuidGenerator;
    private final CouponUsagePort couponUsagePort;

    @Transactional
    public Optional<StockRestoreCommand> expire(UUID orderId, Instant now) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !order.expire(now)) {
            return Optional.empty();
        }

        restoreCoupons(order);
        eventPublisher.publishEvent(new OrderNotificationEvent(
                uuidGenerator.generate(), "PAYMENT_FAILED", "ORDER_SERVICE", "ORDER", order.getId(),
                order.getCustomerId(), order.getOrderNumber().toString(), "EXPIRED", null, now));
        return Optional.of(new StockRestoreCommand(orderId, stockItems(order), ORDER_EXPIRED));
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
