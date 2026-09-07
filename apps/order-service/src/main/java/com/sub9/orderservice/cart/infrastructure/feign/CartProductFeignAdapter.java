package com.sub9.orderservice.cart.infrastructure.feign;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.application.dto.CartItemProducInfo;
import com.sub9.orderservice.cart.application.port.CartProductPort;
import com.sub9.orderservice.cart.infrastructure.feign.exception.CartProductClientErrorCode;
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
  public List<CartItemProducInfo> getCartItemProducts(List<UUID> skuIds) {
    throw new UnsupportedOperationException("개발 중 입니다.");
    //    var cartItemInfos = feignClient.getCartItemProducts(skuIds);
    //
    //    return cartItemInfos;
  }
}
