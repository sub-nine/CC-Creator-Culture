package com.sub9.common.kafka.event;

import java.util.List;
import java.util.UUID;

public record StockRestoreEvent(UUID orderId, List<Item> items, String reason) {
  public record Item(UUID skuId, int quantity) {}
}

