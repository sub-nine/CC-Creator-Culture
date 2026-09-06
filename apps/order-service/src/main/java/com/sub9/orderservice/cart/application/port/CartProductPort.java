package com.sub9.orderservice.cart.application.port;

import com.sub9.orderservice.cart.application.dto.CartItemProducInfo;

import java.util.List;
import java.util.UUID;

public interface CartProductPort {
    void validateSkuForCart(UUID skuId);

    List<CartItemProducInfo> getCartItemProducts(List<UUID> skuIds);
}
