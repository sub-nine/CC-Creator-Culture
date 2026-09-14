package com.sub9.productservice.product.domain.repository;

import com.sub9.productservice.product.domain.model.Stock;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {
  Stock save(Stock stock);

  Optional<Stock> findById(UUID uuid);

  boolean increaseStock(UUID skuId, int quantity);

  boolean decreaseStock(UUID skuId, int quantity);

  boolean adjustStock(UUID uuid, int quantity);
}
