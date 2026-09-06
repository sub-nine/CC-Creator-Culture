package com.sub9.orderservice.cart.presentation.request;

import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record AddCartItemRequest(@NotNull UUID skuId, @Positive int quantity) {
  public AddCartItemCommand toCommand(UUID userId) {
    return new AddCartItemCommand(userId, skuId, quantity);
  }
}
