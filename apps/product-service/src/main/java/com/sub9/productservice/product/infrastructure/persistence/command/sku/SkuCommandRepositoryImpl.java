package com.sub9.productservice.product.infrastructure.persistence.command.sku;

import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.repository.SkuCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SkuCommandRepositoryImpl implements SkuCommandRepository {
  private final SkuCommandJpaRepository skuCommandJpaRepository;

  @Override
  public Sku save(Sku sku) {
    return skuCommandJpaRepository.save(sku);
  }

  @Override
  public List<Sku> findAllByProductIdAndDeletedAtIsNull(UUID productId) {
    return skuCommandJpaRepository.findAllByProductIdAndDeletedAtIsNull(productId);
  }

  @Override
  public Optional<Sku> findByIdAndProductIdAndDeletedAtIsNull(UUID skuId, UUID productId) {
    return skuCommandJpaRepository.findByIdAndProductIdAndDeletedAtIsNull(skuId, productId);
  }

  @Override
  public long countByProductIdAndDeletedAtIsNull(UUID productId) {
    return skuCommandJpaRepository.countByProductIdAndDeletedAtIsNull(productId);
  }

  @Override
  public Optional<Sku> findByProductIdAndIsDefaultTrue(UUID productId) {
    return skuCommandJpaRepository.findByProductIdAndIsDefaultTrue(productId);
  }
}
