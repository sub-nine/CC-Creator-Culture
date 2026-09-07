package com.sub9.orderservice.order.infrastructure.persistence;

import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> findByIdForUpdate(UUID orderId) {
        return orderJpaRepository.findByIdForUpdate(orderId);
    }

    @Override
    public Optional<Order> findByOrderItemIdForUpdate(UUID orderItemId) {
        return orderJpaRepository.findByOrderItemIdForUpdate(orderItemId);
    }

}
