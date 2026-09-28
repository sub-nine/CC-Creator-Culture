package com.sub9.productservice.product.infrastructure.persistence.query;

import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ProductQueryJpaRepository extends Repository<Product, UUID> {
  boolean existsByIdAndDeletedAtIsNullAndStatusNot(UUID productId, ProductStatus status);

  @Query(
  """
  SELECT count(s) > 0
  FROM Sku s, Product p
  WHERE s.productId = p.id
  AND s.id = :skuId
  AND p.creatorId = :creatorId
  AND p.deletedAt IS NULL
  AND s.deletedAt IS NULL
  """)
  boolean existsSkuOwnedByCreatorId(UUID creatorId, UUID skuId);
}
