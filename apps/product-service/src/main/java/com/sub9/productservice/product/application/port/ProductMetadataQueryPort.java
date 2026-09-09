package com.sub9.productservice.product.application.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ProductMetadataQueryPort {
    // TODO: 차후 Page<UUID> 기반 검색으로 수정 필요
    Set<UUID> findProductIdsByMetadataKeyword(String keyword, long limit);

    ProductMetadataInfo getProductMetadata(UUID productId);

    record ProductMetadataInfo(
            List<CategoryInfo> categories,
            List<HashtagInfo> hashtags
    ) {
    }

    record CategoryInfo(UUID categoryId, String name) {
    }

    record HashtagInfo(UUID hashTagId, String name) {
    }
}
