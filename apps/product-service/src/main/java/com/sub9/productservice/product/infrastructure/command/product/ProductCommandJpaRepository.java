package com.sub9.productservice.product.infrastructure.command.product;

import com.sub9.productservice.product.domain.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductCommandJpaRepository extends JpaRepository<Product, UUID> {
  Optional<Product> findByIdAndDeletedAtIsNull(UUID productId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
          select p
          from Product p
          where p.id = :productId
            and p.deletedAt is null
          """)
  Optional<Product> findByIdForUpdate(@Param("productId") UUID productId);

}
