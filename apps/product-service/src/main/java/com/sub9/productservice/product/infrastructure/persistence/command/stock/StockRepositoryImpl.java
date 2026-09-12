package com.sub9.productservice.product.infrastructure.persistence.command.stock;

import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.domain.repository.StockRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockRepositoryImpl implements StockRepository {
  private final StockCommandJpaRepository stockCommandJpaRepository;

  @Override
  public Stock save(Stock stock) {
    return stockCommandJpaRepository.save(stock);
  }

  @Override
  public Optional<Stock> findById(UUID uuid) {
    return stockCommandJpaRepository.findById(uuid);
  }

  @Override
  public boolean increaseStock(UUID skuId, int quantity) {
    return stockCommandJpaRepository.increaseStock(skuId, quantity) > 0;
  }

  @Override
  public boolean decreaseStock(UUID skuId, int quantity) {
    return stockCommandJpaRepository.decreaseStock(skuId, quantity) > 0;
  }

  @Override
  public boolean adjustStock(UUID skuId, int quantity) {
    return stockCommandJpaRepository.adjustStock(skuId, quantity) > 0;
  }
}
