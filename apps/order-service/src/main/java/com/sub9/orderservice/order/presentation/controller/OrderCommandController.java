package com.sub9.orderservice.order.presentation.controller;

import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import com.sub9.orderservice.order.application.service.CreateOrderCommand;
import com.sub9.orderservice.order.application.service.OrderCreationResult;
import com.sub9.orderservice.order.application.service.OrderCreationService;
import com.sub9.orderservice.order.presentation.request.CreateOrderRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderCommandController {

    private final OrderCreationService orderCreationService;

    @PostMapping
    public ResponseEntity<Object> createOrder(
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderCreationResult result = orderCreationService.create(
                principal.userId(),
                idempotencyKey,
                toCommand(request));
        return ResponseEntity.status(result.httpStatus()).body(result.responseBody());
    }

    private CreateOrderCommand toCommand(CreateOrderRequest request) {
        List<CreateOrderCommand.Item> items = request.items().stream()
                .map(item -> new CreateOrderCommand.Item(item.cartItemId(), item.userCouponId()))
                .toList();
        CreateOrderRequest.ShippingAddress address = request.shippingAddress();
        return new CreateOrderCommand(
                items,
                new CreateOrderCommand.ShippingAddress(
                        address.recipientName(),
                        address.recipientPhone(),
                        address.postalCode(),
                        address.addressLine1(),
                        address.addressLine2()));
    }
}
