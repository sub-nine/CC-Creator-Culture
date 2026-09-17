package com.sub9.productservice.product.infrastructure.client;

import java.util.List;
import java.util.UUID;

import com.sub9.productservice.common.config.feign.OpenFeignConfig;
import com.sub9.productservice.product.application.port.out.product.CreatorNameInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "user-service",
    path = "/internal/v1/creators/names",
    configuration = OpenFeignConfig.class)
public interface ProductUserFeignClient {
  @PostMapping
  List<CreatorNameInfo> getCreatorNamesByIds(@RequestBody List<UUID> creatorIds);
}
