package com.sub9.productservice.product.infrastructure.command.stock;

import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.domain.repository.StockCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StockCommandRepositoryAdapter implements StockCommandRepository {
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
  public boolean existsById(UUID skuId) {
    return stockCommandJpaRepository.existsById(skuId);
  }

  @Override
  public boolean increaseStock(UUID skuId, int quantity) {
    return stockCommandJpaRepository.increaseStock(skuId, quantity);
  }

  @Override
  public boolean decreaseStock(UUID skuId, int quantity) {
    return stockCommandJpaRepository.decreaseStock(skuId, quantity);
  }
}
