package com.sub9.orderservice.cart.presentation.request;

import com.sub9.orderservice.cart.application.dto.UpdateCartItemCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public record UpdateCartItemRequest(
    @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
    @Max(value = 99, message = "수량은 최대 99개까지 가능합니다.")
    int quantity) {
  public UpdateCartItemCommand toCommand(UUID userId, UUID cartId) {
    return new UpdateCartItemCommand(userId, cartId, quantity);
  }
}
