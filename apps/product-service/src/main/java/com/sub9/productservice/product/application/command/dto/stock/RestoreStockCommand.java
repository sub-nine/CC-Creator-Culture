package com.sub9.productservice.product.application.command.dto.stock;

import com.sub9.productservice.product.domain.model.StockHistoryReason;
import java.util.List;
import java.util.UUID;

public record RestoreStockCommand(UUID orderId, List<Item> items, StockHistoryReason reason) {
  public record Item(UUID skuId, int quantity) {}
}
