package com.sub9.productservice.product.presentation.command.dto.reqeust.stock;

import com.sub9.productservice.product.application.command.dto.stock.RestoreStockCommand;
import com.sub9.productservice.product.domain.model.StockHistoryReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record RestoreStockRequest(
    @NotNull UUID orderId, @NotEmpty List<@Valid Item> items, @NotNull String reason) {
  public record Item(@NotNull UUID skuId, @Positive int quantity) {}

  public RestoreStockCommand toCommand() {
    return new RestoreStockCommand(
        orderId,
        items.stream()
            .map(item -> new RestoreStockCommand.Item(item.skuId(), item.quantity()))
            .toList(),
        StockHistoryReason.valueOf(reason));
  }
}
