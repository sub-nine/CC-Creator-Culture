package com.sub9.productservice.product.infrastructure.persistence.command.stock;

import com.sub9.productservice.product.domain.model.StockHistory;
import com.sub9.productservice.product.domain.repository.StockHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockHistoryRepositoryImpl implements StockHistoryRepository {
  private final StockHistoryCommandJpaRepository jpaRepository;

  @Override
  public boolean insertIfAbsent(StockHistory stockHistory) {
    return jpaRepository.insertIfAbsent(
            stockHistory.getId(),
            stockHistory.getOrderId(),
            stockHistory.getSkuId(),
            stockHistory.getQuantity(),
            stockHistory.getReason().name(),
            stockHistory.getCreatedAt())
        > 0;
  }

  @Override
  public StockHistory save(StockHistory stockHistory) {
    return jpaRepository.save(stockHistory);
  }
}
