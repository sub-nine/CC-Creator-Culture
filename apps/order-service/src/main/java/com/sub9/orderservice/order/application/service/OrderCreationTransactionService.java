package com.sub9.orderservice.order.application.service;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.AppliedCoupon;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import com.sub9.orderservice.order.presentation.response.CreateOrderResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCreationTransactionService {

    private final CouponUsagePort couponUsagePort;
    private final OrderRepository orderRepository;
    private final OrderCommandIdempotencyService idempotencyService;

    @Transactional
    public void save(UUID commandRequestId, Order order, List<AppliedCoupon> appliedCoupons,
            ApiResponse<CreateOrderResponse> responseBody) {
        if (!appliedCoupons.isEmpty()) {
            couponUsagePort.markUsed(order.getId(), appliedCoupons);
        }
        Order savedOrder = orderRepository.save(order);
        idempotencyService.completeSuccess(commandRequestId, savedOrder, 201, responseBody);
    }
}
