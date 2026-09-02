package com.sub9.productservice.product.infrastructure.command.product;

import com.sub9.productservice.product.domain.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductCommandJpaRepository extends JpaRepository<Product, UUID> {
  Optional<Product> findByIdAndDeletedAtIsNull(UUID productId);
}
