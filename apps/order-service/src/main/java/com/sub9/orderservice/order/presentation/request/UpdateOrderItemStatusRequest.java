package com.sub9.orderservice.order.presentation.request;

import com.sub9.orderservice.order.domain.model.OrderItemStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderItemStatusRequest(
        @NotNull(message = "주문 상품 상태는 필수입니다.")
        OrderItemStatus status
) {
}
