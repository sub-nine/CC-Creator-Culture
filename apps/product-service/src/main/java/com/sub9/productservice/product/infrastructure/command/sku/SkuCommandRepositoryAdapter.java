package com.sub9.productservice.product.infrastructure.command.sku;

import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.repository.SkuCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SkuCommandRepositoryAdapter implements SkuCommandRepository {
  private final SkuCommandJpaRepository skuCommandJpaRepository;

  @Override
  public Sku save(Sku sku) {
    return skuCommandJpaRepository.save(sku);
  }
}
