package com.sub9.orderservice.order.infrastructure.persistence;

import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    @Override
    public List<UUID> findExpiredPendingOrderIds(Instant now, int limit) {
        Objects.requireNonNull(now, "주문 만료 확인 시각은 필수입니다.");
        if (limit < 1) {
            throw new IllegalArgumentException("만료 주문 조회 개수는 한 개 이상이어야 합니다.");
        }
        return orderJpaRepository.findExpiredOrderIds(
                OrderStatus.PENDING_PAYMENT,
                now,
                PageRequest.of(0, limit));
    }

}
