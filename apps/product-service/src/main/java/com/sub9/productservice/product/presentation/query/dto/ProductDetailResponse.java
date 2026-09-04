package com.sub9.productservice.product.presentation.query.dto;

import com.sub9.productservice.product.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductDetailResponse(
    UUID productId,
    UUID creatorId,
    String name,
    String content,
    ProductStatus status,
    Long viewCount,
    BigDecimal averageRating,
    Long reviewCount,
    CategoryInfo category,
    List<HashtagInfo> hashtags,
    List<SkuInfo> skus) {

  public record CategoryInfo(UUID categoryId, String name) {}

  public record HashtagInfo(UUID hashtagId, String name) {}

  public record SkuInfo(UUID skuId, String name, Long price, boolean isDefault, int quantity) {}
}
