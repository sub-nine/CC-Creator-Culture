package com.sub9.productservice.product.application.query.dto;

import com.sub9.productservice.product.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductInfo(
    UUID productId,
    UUID creatorId,
    String creatorName,
    String name,
    ProductStatus status,
    BigDecimal averageRating,
    Long reviewCount,
    Long price,
    int quantity,
    String imageKey) {
  public static ProductInfo of(ProductInfo info, String creatorName) {
    return new ProductInfo(
        info.productId(),
        info.creatorId(),
        creatorName,
        info.name(),
        info.status(),
        info.averageRating(),
        info.reviewCount(),
        info.price(),
        info.quantity(),
        info.imageKey());
  }
}
