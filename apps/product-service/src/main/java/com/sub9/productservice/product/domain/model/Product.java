package com.sub9.productservice.product.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.common.entity.BaseEntity;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "p_products",
    indexes = {@Index(name = "idx_products_creator_id", columnList = "creator_id")})
public class Product extends BaseEntity {
  @Id
  @Column(name = "product_id")
  private UUID id;

  @Column(nullable = false)
  private UUID creatorId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  private Long viewCount;

  @Column(precision = 2, scale = 1)
  private BigDecimal averageRating;

  private Long reviewCount;

  @Column(length = 20, nullable = false)
  @Enumerated(EnumType.STRING)
  private ProductStatus status;

  public static Product create(UUID creatorId, String name, String content) {
    Product product = new Product();
    product.id = UuidCreator.getTimeOrderedEpoch();
    product.creatorId = creatorId;
    product.name = name;
    product.content = content;
    product.viewCount = 0L;
    product.averageRating = null;
    product.reviewCount = 0L;
    product.status = ProductStatus.ACTIVE;

    return product;
  }

  public void validateOwner(UUID creatorId) {
    if (!Objects.equals(creatorId, this.creatorId)) {
      throw new BusinessException(ProductErrorCode.PRODUCT_ACCESS_DENIED);
    }
  }
}
