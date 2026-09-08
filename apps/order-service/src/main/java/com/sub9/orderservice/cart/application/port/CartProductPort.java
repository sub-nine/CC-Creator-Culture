package com.sub9.orderservice.cart.application.port;

import com.sub9.orderservice.cart.application.dto.CartItemInfo;
import com.sub9.orderservice.cart.application.dto.CartProductInfo;

import java.util.List;
import java.util.UUID;

public interface CartProductPort {
    void validateSkuForCart(UUID skuId);

    List<CartProductInfo> getCartItemProducts(List<UUID> skuIds);
}
