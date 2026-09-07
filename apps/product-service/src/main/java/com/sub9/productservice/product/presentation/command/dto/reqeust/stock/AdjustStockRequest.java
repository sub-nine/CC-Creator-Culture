package com.sub9.productservice.product.presentation.command.dto.reqeust.stock;

import com.sub9.productservice.product.application.command.dto.stock.AdjustStockCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AdjustStockRequest(@NotNull int quantity) {
  public AdjustStockCommand toCommand(UUID userId, UUID skuId) {
    return new AdjustStockCommand(userId, skuId, quantity);
  }
}
