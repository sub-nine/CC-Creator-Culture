package com.sub9.orderservice.cart.infrastructure.client;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.application.dto.CartProductInfo;
import com.sub9.orderservice.cart.application.port.out.CartProductPort;
import com.sub9.orderservice.cart.infrastructure.client.exception.CartProductClientErrorCode;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartProductFeignAdapter implements CartProductPort {
  private final CartProductFeignClient feignClient;
  private static final String PRODUCT_SERVICE = "productService";

  @Override
  @CircuitBreaker(name = PRODUCT_SERVICE, fallbackMethod = "getValidatedProductIdForCartFallback")
  public UUID getValidatedProductIdForCart(UUID skuId) {
    UUID productId = feignClient.getValidatedProductIdForCart(skuId);

    if (productId == null) {
      throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    return productId;
  }

  @Override
  @CircuitBreaker(name = PRODUCT_SERVICE, fallbackMethod = "getCartItemProductsFallback")
  public List<CartProductInfo> getCartItemProducts(List<UUID> skuIds) {
    return feignClient.getProductsForCart(skuIds);
  }

  private UUID getValidatedProductIdForCartFallback(UUID skuId, Throwable throwable) {
    if (throwable instanceof FeignException.NotFound
        || throwable instanceof FeignException.Conflict
        || throwable instanceof FeignException.BadRequest) {
      throw new BusinessException(CartProductClientErrorCode.INVALID_CART_PRODUCT);
    }

    log.warn(
        "[Product-Service Fallback] skuId = {}, cause = {}",
        skuId,
        throwable.getClass().getSimpleName());

    throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
  }

  private List<CartProductInfo> getCartItemProductsFallback(
      List<UUID> skuIds, Throwable throwable) {
    if (throwable instanceof FeignException.NotFound
        || throwable instanceof FeignException.Conflict
        || throwable instanceof FeignException.BadRequest) {
      throw new BusinessException(CartProductClientErrorCode.INVALID_CART_PRODUCT);
    }

    log.warn(
        "[Product-Service Fallback] skuCount = {}, cause = {}",
        skuIds.size(),
        throwable.getClass().getSimpleName());

    throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
  }
}
