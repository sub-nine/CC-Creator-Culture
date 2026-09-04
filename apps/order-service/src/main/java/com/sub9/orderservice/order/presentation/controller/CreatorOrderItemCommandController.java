package com.sub9.orderservice.order.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import com.sub9.orderservice.order.application.service.OrderItemStatusService;
import com.sub9.orderservice.order.presentation.request.UpdateOrderItemStatusRequest;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorOrderItemDetail;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/creator/order-items")
public class CreatorOrderItemCommandController {

    private final OrderItemStatusService orderItemStatusService;

    @PatchMapping("/{orderItemId}/status")
    public ApiResponse<CreatorOrderItemDetail> updateStatus(
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
            @PathVariable UUID orderItemId,
            @Valid @RequestBody UpdateOrderItemStatusRequest request) {
        return ApiResponse.success(
                "주문 상품 상태 변경 성공",
                orderItemStatusService.update(principal.userId(), orderItemId, request.status()));
    }
}
