package com.sub9.productservice.product.application.command.dto.stock;

import java.util.List;
import java.util.UUID;

public record DeductStockCommand(UUID orderId, List<Item> items) {
  public record Item(UUID skuId, int quantity) {}
}
