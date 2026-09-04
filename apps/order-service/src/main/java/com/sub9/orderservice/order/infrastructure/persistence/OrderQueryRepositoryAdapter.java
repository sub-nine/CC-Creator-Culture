package com.sub9.orderservice.order.infrastructure.persistence;

import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.repository.OrderQueryRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderQueryRepositoryAdapter implements OrderQueryRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public Optional<Order> findDetailByOrderNumber(OrderNumber orderNumber) {
        return orderJpaRepository.findByOrderNumber(orderNumber);
    }

    @Override
    public Page<Order> findAllByCustomerId(UUID customerId, Pageable pageable) {
        return orderJpaRepository.findAllByCustomerId(customerId, newestFirst(pageable));
    }

    @Override
    public Page<Order> findAllOrders(Pageable pageable) {
        return orderJpaRepository.findAll(newestFirst(pageable));
    }

    @Override
    public Page<OrderItem> findAllItemsByCreatorId(UUID creatorId, Pageable pageable) {
        return orderItemJpaRepository.findAllVisibleByCreatorId(
                creatorId,
                OrderStatus.creatorVisibleStatuses(),
                newestFirst(pageable));
    }

    @Override
    public Optional<OrderItem> findItemDetailById(UUID orderItemId) {
        return orderItemJpaRepository.findDetailById(orderItemId);
    }

    private static Pageable newestFirst(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
    }
}
