package com.sub9.orderservice.order.presentation.controller;

import com.sub9.orderservice.order.application.service.OrderQueryService;
import com.sub9.orderservice.order.presentation.response.ProductPurchaseInfo;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalOrderQueryController {

    private final OrderQueryService orderQueryService;

    @GetMapping("/internal/v1/orders/purchase-status")
    public ProductPurchaseInfo getPurchaseStatus(
            @RequestParam UUID userId,
            @RequestParam UUID orderItemId) {
        return orderQueryService.getPurchaseStatus(userId, orderItemId);
    }
}
