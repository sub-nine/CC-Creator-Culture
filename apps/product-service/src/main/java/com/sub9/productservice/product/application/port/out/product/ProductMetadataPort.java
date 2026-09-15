package com.sub9.productservice.product.application.port.out.product;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface ProductMetadataPort {

  // Product에서 페이징 처리 및 조립 예정이므로 누락 방지를 위해 일치하는 Product Id 전체 반환
  ProductSearchMetadata findProductIdsByKeyword(String keyword);

  // 상품 상세 조회나 목록 조회 시 필요
  Map<UUID, ProductMetadataInfo> getProductMetadata(List<UUID> productIds);

  record ProductSearchMetadata(
      Set<UUID> categoryProductIds,
      Set<UUID> hashtagProductIds
  ) {}

  record ProductMetadataInfo(
      List<CategoryInfo> categories,
      List<HashtagInfo> hashtags
  ) {}

  record CategoryInfo(UUID productId, UUID categoryId, String name) {}

  record HashtagInfo(UUID productId, UUID hashTagId, String name) {}
}
