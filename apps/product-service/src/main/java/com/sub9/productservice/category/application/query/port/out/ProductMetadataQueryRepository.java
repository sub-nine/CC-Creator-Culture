package com.sub9.productservice.category.application.query.port.out;

import com.sub9.productservice.product.application.port.out.product.ProductMetadataQueryPort;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ProductMetadataQueryRepository {
    Set<UUID> findProductIdsByHashtagKeyword(String keyword, long limit);

    List<ProductMetadataQueryPort.HashtagInfo> findHashtagsByProductId(UUID productId);

    List<ProductMetadataQueryPort.CategoryInfo> findCategoriesByProductId(UUID productId);

    Set<UUID> findProductIdsByCategoryKeyword(String keyword, long limit);
}
