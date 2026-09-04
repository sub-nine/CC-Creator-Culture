package com.sub9.orderservice.order.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import com.sub9.orderservice.order.application.service.OrderQueryService;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.AdminOrderDetail;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.AdminOrderSummary;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorOrderItemDetail;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorOrderItemSummary;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CustomerOrderDetail;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CustomerOrderSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderQueryController {

    private static final String SUCCESS_MESSAGE = "주문 조회 성공";

    private final OrderQueryService orderQueryService;

    @GetMapping("/api/v1/orders")
    public ApiResponse<Page<CustomerOrderSummary>> getCustomerOrders(
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size) {
        return ApiResponse.success(
                SUCCESS_MESSAGE,
                orderQueryService.getCustomerOrders(principal.userId(), PageRequest.of(page, size)));
    }

    @GetMapping("/api/v1/orders/{orderNumber}")
    public ApiResponse<CustomerOrderDetail> getCustomerOrder(
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
            @PathVariable String orderNumber) {
        return ApiResponse.success(
                SUCCESS_MESSAGE,
                orderQueryService.getCustomerOrder(principal.userId(), parseOrderNumber(orderNumber)));
    }

    @GetMapping("/api/v1/creator/order-items")
    public ApiResponse<Page<CreatorOrderItemSummary>> getCreatorOrderItems(
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size) {
        return ApiResponse.success(
                SUCCESS_MESSAGE,
                orderQueryService.getCreatorOrderItems(principal.userId(), PageRequest.of(page, size)));
    }

    @GetMapping("/api/v1/creator/order-items/{orderItemId}")
    public ApiResponse<CreatorOrderItemDetail> getCreatorOrderItem(
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
            @PathVariable UUID orderItemId) {
        return ApiResponse.success(
                SUCCESS_MESSAGE,
                orderQueryService.getCreatorOrderItem(principal.userId(), orderItemId));
    }

    @GetMapping("/api/v1/admin/orders")
    public ApiResponse<Page<AdminOrderSummary>> getAdminOrders(
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size) {
        return ApiResponse.success(
                SUCCESS_MESSAGE,
                orderQueryService.getAdminOrders(PageRequest.of(page, size)));
    }

    @GetMapping("/api/v1/admin/orders/{orderNumber}")
    public ApiResponse<AdminOrderDetail> getAdminOrder(@PathVariable String orderNumber) {
        return ApiResponse.success(
                SUCCESS_MESSAGE,
                orderQueryService.getAdminOrder(parseOrderNumber(orderNumber)));
    }

    private static OrderNumber parseOrderNumber(String value) {
        try {
            return OrderNumber.from(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
    }
}
