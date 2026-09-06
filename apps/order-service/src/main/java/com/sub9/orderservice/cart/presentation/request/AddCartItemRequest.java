package com.sub9.orderservice.cart.presentation.request;

import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record AddCartItemRequest(
    @NotNull(message = "상품을 선택해주세요.") UUID skuId,
    @Positive(message = "수량은 1개 이상이어야 합니다.") int quantity) {
  public AddCartItemCommand toCommand(UUID userId) {
    return new AddCartItemCommand(userId, skuId, quantity);
  }
}
