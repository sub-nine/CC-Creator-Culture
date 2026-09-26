package com.sub9.productservice.product.domain.model;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.common.entity.BaseEntity;
import com.sub9.productservice.product.domain.exception.SkuErrorCode;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "p_skus",
    indexes = {@Index(name = "idx_skus_product_id", columnList = "product_id")},
    check = {@CheckConstraint(name = "ck_skus_price", constraint = "price >= 0")})
public class Sku extends BaseEntity {
  @Column(nullable = false)
  private UUID productId;

  @Column(nullable = false, length = 25)
  private String name;

  @Column(nullable = false)
  private Long price;

  @Column(nullable = false)
  private boolean isDefault;

  public static Sku create(UUID productId, String name, Long price, boolean isDefault) {
    validatePrice(price);

    Sku sku = new Sku();
    sku.productId = productId;
    sku.name = name;
    sku.price = price;
    sku.isDefault = isDefault;
    return sku;
  }

  public void update(String name, Long price, boolean isDefault) {
    if (this.isDefault && !isDefault) {
      throw new BusinessException(SkuErrorCode.DEFAULT_SKU_CANNOT_UNSET);
    }

    validatePrice(price);

    this.name = name;
    this.price = price;
    this.isDefault = isDefault;
  }

  public void deleteOption(UUID creatorId, long activeSkuCount) {
    if (this.isDefault()) {
      throw new BusinessException(SkuErrorCode.DEFAULT_SKU_CANNOT_DELETED);
    }

    if (activeSkuCount <= 1) {
      throw new BusinessException(SkuErrorCode.SKU_REQUIRED);
    }

    super.delete(creatorId);
  }

  public void unsetDefault() {
    this.isDefault = false;
  }

  private static void validatePrice(Long price) {
    if (price == null || price < 0) {
      throw new BusinessException(SkuErrorCode.INVALID_SKU_PRICE);
    }
  }
}
