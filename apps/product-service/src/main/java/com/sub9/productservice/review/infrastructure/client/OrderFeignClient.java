package com.sub9.productservice.review.infrastructure.client;

import java.util.UUID;

import com.sub9.productservice.common.config.feign.OpenFeignConfig;
import com.sub9.productservice.review.application.port.out.dto.ProductPurchaseInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "order-service",
    path = "/internal/v1/orders",
    configuration = OpenFeignConfig.class)
public interface OrderFeignClient {
  @GetMapping("/purchase-status")
  ProductPurchaseInfo getPurchaseInfo(@RequestParam UUID userId, @RequestParam UUID orderItemId);
}
