package com.sub9.productservice.product.application.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ProductMetadataQueryPort {
  Set<UUID> findProductIdsByKeyword(String keyword);

  ProductMetadataInfo getProductMetadata(UUID productId);

  record ProductMetadataInfo(
      List<CategoryInfo> categories,
      List<HashtagInfo> hashtags) {}

  record CategoryInfo(UUID categoryId, String name) {}

  record HashtagInfo(UUID hashTagId, String name) {}
}
