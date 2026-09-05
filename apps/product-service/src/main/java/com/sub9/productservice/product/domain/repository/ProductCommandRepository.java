package com.sub9.productservice.product.domain.repository;

import com.sub9.productservice.product.domain.model.Product;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductCommandRepository {
  Product save(Product product);

  Optional<Product> findByIdAndDeletedAtIsNull(UUID productId);

  Optional<Product> findByIdForUpdate( UUID productId);
}
