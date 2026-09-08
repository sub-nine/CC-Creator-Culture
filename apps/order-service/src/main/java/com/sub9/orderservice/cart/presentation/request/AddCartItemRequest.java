package com.sub9.orderservice.cart.presentation.request;

import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddCartItemRequest(
    @NotNull(message = "상품을 선택해주세요.") UUID skuId,
    @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
    @Max(value = 99, message = "수량은 최대 99개까지 가능합니다.")
    int quantity) {
  public AddCartItemCommand toCommand(UUID userId) {
    return new AddCartItemCommand(userId, skuId, quantity);
  }
}
