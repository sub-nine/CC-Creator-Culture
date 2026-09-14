package com.sub9.productservice.review.infrastructure.client;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.review.application.port.out.ReviewOrderQueryPort;
import com.sub9.productservice.review.application.port.out.dto.ProductPurchaseInfo;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFeignAdapter implements ReviewOrderQueryPort {
  private final OrderFeignClient feignClient;

  @Override
  @CircuitBreaker(name = "orderService", fallbackMethod = "hasPurchasedProductFallback")
  public ProductPurchaseInfo hasPurchasedProduct(UUID userId, UUID orderItemId) {
    return feignClient.hasPurchased(userId, orderItemId);
  }

  private ProductPurchaseInfo hasPurchasedProductFallback(UUID userId, UUID orderItemId, Throwable throwable) {
    if (throwable instanceof FeignException.BadRequest) {
      throw new BusinessException(CommonErrorCode.BAD_REQUEST);
    }

    log.warn(
        "[Order-Service Fallback] userId = {}, orderItemId = {}, cause = {}",
        userId,
        orderItemId,
        throwable.getClass().getSimpleName());

    throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
  }
}
