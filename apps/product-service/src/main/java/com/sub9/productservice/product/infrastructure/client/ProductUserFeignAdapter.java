package com.sub9.productservice.product.infrastructure.client;

import com.sub9.productservice.product.application.port.out.product.CreatorNameInfo;
import com.sub9.productservice.product.application.port.out.product.ProductUserPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductUserFeignAdapter implements ProductUserPort {
  private static final String USER_SERVICE = "userService";
  private final ProductUserFeignClient feignClient;

  @Override
  @CircuitBreaker(name = USER_SERVICE, fallbackMethod = "getCreatorNamesByIdsFallback")
  public Map<UUID, String> getCreatorNamesByIds(List<UUID> creatorIds) {
    return feignClient.getCreatorNamesByIds(creatorIds).stream()
        .collect(Collectors.toMap(CreatorNameInfo::creatorId, CreatorNameInfo::creatorName));
  }

  private Map<UUID, String> getCreatorNamesByIdsFallback(
      List<UUID> creatorIds, Throwable throwable) {
    log.warn(
        "[User Service Fallback] creatorCount = {}, cause = {}",
        creatorIds.size(),
        throwable.getClass().getSimpleName());
    return Map.of();
  }
}
