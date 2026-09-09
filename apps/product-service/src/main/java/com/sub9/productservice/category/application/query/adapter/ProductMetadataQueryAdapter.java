package com.sub9.productservice.category.application.query.adapter;

import com.sub9.productservice.category.application.query.port.out.ProductMetadataQueryRepository;
import com.sub9.productservice.product.application.port.ProductMetadataQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProductMetadataQueryAdapter implements ProductMetadataQueryPort {
    private final ProductMetadataQueryRepository productMetadataQueryRepository;

    @Override
    public Set<UUID> findProductIdsByMetadataKeyword(String keyword, long limit) {
        Set<UUID> productIdsByHashtagName = productMetadataQueryRepository
                .findProductIdsByHashtagKeyword(keyword, limit);

        Set<UUID> productidsByCategoryNameAndDescription = productMetadataQueryRepository
                .findProductIdsByCategoryKeyword(keyword, limit);

        return Stream.concat(
                productIdsByHashtagName.stream(),
                productidsByCategoryNameAndDescription.stream()
        ).collect(Collectors.toSet());
    }

    @Override
    public ProductMetadataInfo getProductMetadata(UUID productId) {
        List<HashtagInfo> hashtags = productMetadataQueryRepository.findHashtagsByProductId(productId);
        List<CategoryInfo> categories = productMetadataQueryRepository.findCategoriesByProductId(productId);

        return new ProductMetadataInfo(
                categories,
                hashtags
        );
    }
}
