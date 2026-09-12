package com.sub9.productservice.product.domain.repository;

import com.sub9.productservice.product.domain.model.StockHistory;

public interface StockHistoryCommandRepository {
  boolean insertIfAbsent(StockHistory stockHistory);

  StockHistory save(StockHistory stockHistory);
}
