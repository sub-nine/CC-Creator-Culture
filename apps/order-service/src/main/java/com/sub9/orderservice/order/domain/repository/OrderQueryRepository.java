package com.sub9.orderservice.order.domain.repository;

import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderQueryRepository {

    Optional<Order> findDetailByOrderNumber(OrderNumber orderNumber);

    Page<Order> findAllOrders(Pageable pageable);
}
