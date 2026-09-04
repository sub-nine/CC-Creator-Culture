package com.sub9.productservice.product.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "p_stocks",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_stocks_sku_id",
          columnNames = {"sku_id"})
    },
    check = {@CheckConstraint(name = "ck_stocks_quantity", constraint = "quantity >= 0")})
public class Stock {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID skuId;

  @Column(nullable = false)
  private int quantity;

  private LocalDateTime updatedAt;

  public static Stock create(UUID skuId, int quantity) {
    if (quantity < 0) {
      throw new BusinessException(ProductErrorCode.INVALID_STOCK_QUANTITY);
    }

    Stock stock = new Stock();
    stock.id = UuidCreator.getTimeOrderedEpoch();
    stock.skuId = skuId;
    stock.quantity = quantity;

    return stock;
  }
}
