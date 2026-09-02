package com.sub9.productservice.product.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.common.entity.BaseEntity;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "p_skus",
    indexes = {@Index(name = "idx_skus_product_id", columnList = "product_id")},
    check = {@CheckConstraint(name = "ck_skus_price", constraint = "price >= 0")})
// TODO : defalut 1개만 적용되도록 유니크 걸려면
//        Partial Index 필요,  DB 마이그레이션 툴 사용 여부 확인 필요
public class Sku extends BaseEntity {
  @Id
  @Column(name = "sku_id")
  private UUID id;

  @Column(nullable = false)
  private UUID productId;

  @Column(nullable = false, length = 25)
  private String name;

  @Column(nullable = false)
  private Long price;

  @Column(nullable = false)
  private boolean isDefault;

  public static Sku create(UUID productId, String name, Long price, boolean isDefault) {
    if (price == null || price < 0) {
      throw new BusinessException(ProductErrorCode.INVALID_SKU_PRICE);
    }

    Sku sku = new Sku();
    sku.id = UuidCreator.getTimeOrderedEpoch();
    sku.productId = productId;
    sku.name = name;
    sku.price = price;
    sku.isDefault = isDefault;
    return sku;
  }
}
