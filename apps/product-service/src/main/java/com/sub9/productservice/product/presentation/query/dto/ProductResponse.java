package com.sub9.productservice.product.presentation.query.dto;

import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.presentation.support.ImageUrlUtils;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
    UUID productId,
    String name,
    ProductStatus status,
    BigDecimal averageRating,
    Long reviewCount,
    Long price,
    int quantity,
    String imageUrl) {

  public static ProductResponse of(ProductInfo info, String publicUrl) {
    return new ProductResponse(
        info.productId(),
        info.name(),
        info.status(),
        info.averageRating(),
        info.reviewCount(),
        info.price(),
        info.quantity(),
        ImageUrlUtils.toImageUrl(publicUrl, info.imageKey()));
  }
}
