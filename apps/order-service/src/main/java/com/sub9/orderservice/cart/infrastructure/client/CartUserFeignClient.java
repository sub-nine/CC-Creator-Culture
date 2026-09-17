package com.sub9.orderservice.cart.infrastructure.client;

import com.sub9.orderservice.cart.application.dto.CreatorNameInfo;
import com.sub9.orderservice.config.OpenFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "user-service",
    path = "/internal/v1/creators/names",
    configuration = OpenFeignConfig.class)
public interface CartUserFeignClient {
  @PostMapping
  List<CreatorNameInfo> getCreatorNamesByIds(@RequestBody List<UUID> creatorIds);
}
