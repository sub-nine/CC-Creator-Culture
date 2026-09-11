package com.sub9.productservice.product.presentation.query.dto;

import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.presentation.support.ImageUrlUtils;
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
    List<ProductDetailInfo.CategoryInfo> categories,
    List<ProductDetailInfo.HashtagInfo> hashtags,
    List<ProductDetailInfo.SkuInfo> skus,
    List<ImageResponse> images) {

  public static ProductDetailResponse of(ProductDetailInfo productDetailInfo, String publicUrl) {

    var images =
        productDetailInfo.images().stream()
            .map(
                image ->
                    new ImageResponse(
                        image.imageId(),
                        ImageUrlUtils.toImageUrl(publicUrl, image.imageKey()),
                        image.sortOrder()))
            .toList();

    return new ProductDetailResponse(
        productDetailInfo.productId(),
        productDetailInfo.creatorId(),
        productDetailInfo.name(),
        productDetailInfo.content(),
        productDetailInfo.status(),
        productDetailInfo.viewCount(),
        productDetailInfo.averageRating(),
        productDetailInfo.reviewCount(),
        productDetailInfo.categories(),
        productDetailInfo.hashtags(),
        productDetailInfo.skus(),
        images);
  }

  public record ImageResponse(UUID imageId, String imageUrl, int sortOrder) {}
}
