package com.sub9.productservice.product.infrastructure.command.product;

import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.repository.ProductCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductCommandRepositoryAdapter implements ProductCommandRepository {
  private final ProductCommandJpaRepository jpaRepository;

  @Override
  public Product save(Product product) {
    return jpaRepository.save(product);
  }

  @Override
  public Optional<Product> findByIdAndDeletedAtIsNull(UUID productId) {
    return jpaRepository.findByIdAndDeletedAtIsNull(productId);
  }

  @Override
  public Optional<Product> findByIdForUpdate(UUID productId) {
    return jpaRepository.findByIdForUpdate(productId);
  }
}
