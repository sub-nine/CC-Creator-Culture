package com.sub9.productservice.product.infrastructure.persistence.command.sku;

import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SkuRepositoryImpl implements SkuRepository {
  private final SkuCommandJpaRepository jpaRepository;

  @Override
  public Sku save(Sku sku) {
    return jpaRepository.save(sku);
  }

  @Override
  public List<Sku> findAllByProductIdAndDeletedAtIsNull(UUID productId) {
    return jpaRepository.findAllByProductIdAndDeletedAtIsNull(productId);
  }

  @Override
  public Optional<Sku> findByIdAndProductIdAndDeletedAtIsNull(UUID skuId, UUID productId) {
    return jpaRepository.findByIdAndProductIdAndDeletedAtIsNull(skuId, productId);
  }

  @Override
  public long countByProductIdAndDeletedAtIsNull(UUID productId) {
    return jpaRepository.countByProductIdAndDeletedAtIsNull(productId);
  }

  @Override
  public Optional<Sku> findByProductIdAndIsDefaultTrue(UUID productId) {
    return jpaRepository.findByProductIdAndIsDefaultTrue(productId);
  }

  @Override
  public void flush() {
    jpaRepository.flush();
  }
}
