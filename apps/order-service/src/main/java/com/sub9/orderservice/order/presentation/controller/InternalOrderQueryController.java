package com.sub9.orderservice.order.presentation.controller;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import com.sub9.orderservice.order.application.service.OrderQueryService;
import com.sub9.orderservice.order.presentation.response.ProductPurchaseInfo;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalOrderQueryController {

    private final OrderQueryService orderQueryService;

    @GetMapping("/internal/v1/orders/purchase-status")
    public ProductPurchaseInfo getPurchaseStatus(
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
            @RequestParam UUID userId,
            @RequestParam UUID orderItemId) {
        if (!principal.userId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return orderQueryService.getPurchaseStatus(userId, orderItemId);
    }
}
