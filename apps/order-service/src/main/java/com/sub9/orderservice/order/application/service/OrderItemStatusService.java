package com.sub9.orderservice.order.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderItemStatus;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorOrderItemDetail;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderItemStatusService {

    private final OrderRepository orderRepository;

    @Transactional
    public CreatorOrderItemDetail update(
            UUID creatorId, UUID orderItemId, OrderItemStatus targetStatus) {
        Order order = orderRepository.findByOrderItemIdForUpdate(orderItemId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderItem item = order.changeItemStatus(creatorId, orderItemId, targetStatus);
        return CreatorOrderItemDetail.from(item);
    }
}
