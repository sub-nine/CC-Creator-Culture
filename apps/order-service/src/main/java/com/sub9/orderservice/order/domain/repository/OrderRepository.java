package com.sub9.orderservice.order.domain.repository;

import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findByIdForUpdate(UUID orderId);

    Optional<Order> findByOrderNumberForUpdate(OrderNumber orderNumber);

    Optional<Order> findByOrderItemIdForUpdate(UUID orderItemId);

    List<UUID> findExpiredPendingOrderIds(Instant now, int limit);
}
