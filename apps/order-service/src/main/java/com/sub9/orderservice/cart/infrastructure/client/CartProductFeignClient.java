package com.sub9.orderservice.cart.infrastructure.client;

import com.sub9.orderservice.cart.application.dto.CartItemInfo;
import com.sub9.orderservice.cart.application.dto.CartProductInfo;
import com.sub9.orderservice.config.OpenFeignConfig;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
    name = "product-service",
    path = "/internal/v1/skus",
    configuration = OpenFeignConfig.class)
public interface CartProductFeignClient {

  @GetMapping("/{skuId}/validation")
  void validateSkuForCart(@PathVariable UUID skuId);

  @PostMapping
  List<CartProductInfo> getProductsForCart(List<UUID> skuIds);
}
