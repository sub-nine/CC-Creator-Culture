package com.sub9.productservice.product.application.query.dto;

import com.sub9.productservice.product.application.port.out.product.ProductMetadataQueryPort;
import com.sub9.productservice.product.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductDetailInfo(
    UUID productId,
    UUID creatorId,
    String name,
    String content,
    ProductStatus status,
    Long viewCount,
    BigDecimal averageRating,
    Long reviewCount,
    List<CategoryInfo> categories,
    List<HashtagInfo> hashtags,
    List<SkuInfo> skus,
    List<ImageInfo> images) {

  public ProductDetailInfo withMetadata(ProductMetadataQueryPort.ProductMetadataInfo metadata) {
    var categories =
        metadata.categories().stream()
            .map(category -> new CategoryInfo(category.categoryId(), category.name()))
            .toList();

    var hashtags =
        metadata.hashtags().stream()
            .map(hashtag -> new HashtagInfo(hashtag.hashTagId(), hashtag.name()))
            .toList();

    return new ProductDetailInfo(
        productId,
        creatorId,
        name,
        content,
        status,
        viewCount,
        averageRating,
        reviewCount,
        categories,
        hashtags,
        skus,
        images);
  }

  public record CategoryInfo(UUID categoryId, String name) {}

  public record HashtagInfo(UUID hashtagId, String name) {}

  public record SkuInfo(UUID skuId, String name, Long price, boolean isDefault, int quantity) {}

  public record ImageInfo(UUID imageId, String imageKey, int sortOrder) {}
}
