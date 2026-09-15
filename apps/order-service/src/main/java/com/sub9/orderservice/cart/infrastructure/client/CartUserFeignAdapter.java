package com.sub9.orderservice.cart.infrastructure.client;

import com.sub9.orderservice.cart.application.port.out.CartUserPort;
import com.sub9.orderservice.cart.application.dto.CreatorNameInfo;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartUserFeignAdapter implements CartUserPort {
  private static final String USER_SERVICE = "userService";
  private final CartUserFeignClient feignClient;

  @Override
  @CircuitBreaker(name = USER_SERVICE, fallbackMethod = "getCreatorNamesByIdsFallback")
  public List<CreatorNameInfo> getCreatorNamesByIds(List<UUID> creatorIds) {
    return feignClient.getCreatorNamesByIds(creatorIds);
  }

  private List<CreatorNameInfo> getCreatorNamesByIdsFallback(
      List<UUID> creatorIds, Throwable throwable) {
    log.warn(
        "[User_service Fallback] creatorCount = {}, cause = {}",
        creatorIds.size(),
        throwable.getClass().getSimpleName());
    return List.of();
  }
}
