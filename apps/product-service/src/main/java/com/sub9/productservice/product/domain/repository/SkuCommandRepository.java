package com.sub9.productservice.product.domain.repository;

import com.sub9.productservice.product.domain.model.Sku;

public interface SkuCommandRepository {
  Sku save(Sku sku);
}
