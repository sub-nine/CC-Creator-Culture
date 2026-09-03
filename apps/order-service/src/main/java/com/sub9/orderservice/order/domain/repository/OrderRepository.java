package com.sub9.orderservice.order.domain.repository;

import com.sub9.orderservice.order.domain.model.Order;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findByIdForUpdate(UUID orderId);
}
