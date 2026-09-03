package com.sub9.productservice.product.domain.repository;

import com.sub9.productservice.product.domain.model.Sku;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkuCommandRepository {
  Sku save(Sku sku);

  List<Sku> findAllByProductIdAndDeletedAtIsNull(UUID productId);

  Optional<Sku> findByIdAndProductIdAndDeletedAtIsNull(UUID skuId, UUID productId);

  long countByProductIdAndDeletedAtIsNull(UUID productId);
}
