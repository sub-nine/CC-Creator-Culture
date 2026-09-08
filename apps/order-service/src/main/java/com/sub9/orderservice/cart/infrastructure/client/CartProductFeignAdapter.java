package com.sub9.orderservice.cart.infrastructure.client;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.application.dto.CartProductInfo;
import com.sub9.orderservice.cart.application.port.CartProductPort;
import com.sub9.orderservice.cart.infrastructure.client.exception.CartProductClientErrorCode;
import feign.FeignException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CartProductFeignAdapter implements CartProductPort {
  private final CartProductFeignClient feignClient;

  @Override
  public void validateSkuForCart(UUID skuId) {
    try {
      feignClient.validateSkuForCart(skuId);
    } catch (FeignException.NotFound | FeignException.Conflict | FeignException.BadRequest e) {
      throw new BusinessException(CartProductClientErrorCode.INVALID_CART_PRODUCT);
    } catch (FeignException e) {
      throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
  }

  @Override
  public List<CartProductInfo> getCartItemProducts(List<UUID> skuIds) {
    try {
       return feignClient.getProductsForCart(skuIds);
    } catch (FeignException.NotFound | FeignException.Conflict | FeignException.BadRequest e) {
      throw new BusinessException(CartProductClientErrorCode.INVALID_CART_PRODUCT);
    } catch (FeignException e) {
      throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}}
