package com.sub9.orderservice.order.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.order.domain.repository.OrderQueryRepository;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.AdminOrderDetail;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.AdminOrderSummary;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorOrderItemDetail;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorOrderItemSummary;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CustomerOrderDetail;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CustomerOrderSummary;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderQueryRepository orderQueryRepository;

    public Page<CustomerOrderSummary> getCustomerOrders(UUID customerId, Pageable pageable) {
        return orderQueryRepository.findAllByCustomerId(customerId, pageable)
                .map(CustomerOrderSummary::from);
    }

    public CustomerOrderDetail getCustomerOrder(UUID customerId, OrderNumber orderNumber) {
        Order order = findOrder(orderNumber);
        if (!order.getCustomerId().equals(customerId)) {
            throw accessDenied();
        }
        return CustomerOrderDetail.from(order);
    }

    public Page<CreatorOrderItemSummary> getCreatorOrderItems(UUID creatorId, Pageable pageable) {
        return orderQueryRepository.findAllItemsByCreatorId(creatorId, pageable)
                .map(CreatorOrderItemSummary::from);
    }

    public CreatorOrderItemDetail getCreatorOrderItem(UUID creatorId, UUID orderItemId) {
        OrderItem item = orderQueryRepository.findItemDetailById(orderItemId)
                .orElseThrow(OrderQueryService::notFound);
        if (!item.getCreatorId().equals(creatorId)
                || !item.getOrder().getStatus().isVisibleToCreator()) {
            throw accessDenied();
        }
        return CreatorOrderItemDetail.from(item);
    }

    public Page<AdminOrderSummary> getAdminOrders(Pageable pageable) {
        return orderQueryRepository.findAllOrders(pageable)
                .map(AdminOrderSummary::from);
    }

    public AdminOrderDetail getAdminOrder(OrderNumber orderNumber) {
        return AdminOrderDetail.from(findOrder(orderNumber));
    }

    private Order findOrder(OrderNumber orderNumber) {
        return orderQueryRepository.findDetailByOrderNumber(orderNumber)
                .orElseThrow(OrderQueryService::notFound);
    }

    private static BusinessException notFound() {
        return new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
    }

    private static BusinessException accessDenied() {
        return new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
    }
}
