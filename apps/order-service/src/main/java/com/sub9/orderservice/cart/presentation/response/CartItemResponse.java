package com.sub9.orderservice.cart.presentation.response;

import com.sub9.orderservice.cart.application.dto.CartItemInfo;
import java.util.UUID;

public record CartItemResponse(
    UUID cartId,
    UUID skuId,
    String productName,
    String skuName,
    String productStatus,
    int quantity,
    long price) {
  public static CartItemResponse from(CartItemInfo cartItemInfo) {
    return new CartItemResponse(
        cartItemInfo.cartId(),
        cartItemInfo.skuId(),
        cartItemInfo.productName(),
        cartItemInfo.skuName(),
        cartItemInfo.productStatus(),
        cartItemInfo.quantity(),
        cartItemInfo.price());
  }
}
