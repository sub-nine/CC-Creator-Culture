package com.sub9.productservice.product.presentation.command.dto.reqeust.stock;

import com.sub9.productservice.product.application.command.dto.stock.DeductStockCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record DeductStockRequest(@NotNull UUID orderId, @NotEmpty List<@Valid Item> items) {
  public record Item(@NotNull UUID skuId, @Positive int quantity) {}

  public DeductStockCommand toCommand() {
    return new DeductStockCommand(
        orderId,
        items.stream()
            .map(item -> new DeductStockCommand.Item(item.skuId(), item.quantity()))
            .toList());
  }
}
