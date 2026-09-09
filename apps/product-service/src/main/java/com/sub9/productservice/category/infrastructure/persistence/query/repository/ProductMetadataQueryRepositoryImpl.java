package com.sub9.productservice.category.infrastructure.persistence.query.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sub9.productservice.category.application.query.port.out.ProductMetadataQueryRepository;
import com.sub9.productservice.category.domain.entity.QCategory;
import com.sub9.productservice.category.domain.entity.QCategoryProduct;
import com.sub9.productservice.category.domain.entity.QHashtag;
import com.sub9.productservice.category.domain.entity.QHashtagProduct;
import com.sub9.productservice.category.domain.model.CategoryStatus;
import com.sub9.productservice.category.infrastructure.persistence.query.support.QuerydslQuerySupport;
import com.sub9.productservice.product.application.port.ProductMetadataQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductMetadataQueryRepositoryImpl implements ProductMetadataQueryRepository {
    private static final QCategory category = QCategory.category;
    private static final QHashtag hashtag = QHashtag.hashtag;
    private static final QHashtagProduct hashtagProduct = QHashtagProduct.hashtagProduct;
    private static final QCategoryProduct categoryProduct = QCategoryProduct.categoryProduct;

    private final JPAQueryFactory queryFactory;

    @Override
    public Set<UUID> findProductIdsByHashtagKeyword(String keyword, long limit) {
        if (keyword == null || keyword.isBlank() || limit < 1) {
            return Set.of();
        }

        List<UUID> productIds = queryFactory
                .select(hashtagProduct.productId)
                .from(hashtagProduct)
                .join(hashtagProduct.hashtag, hashtag)
                .where(
                        hashtagProduct.deletedAt.isNull(),
                        hashtag.deletedAt.isNull(),
                        QuerydslQuerySupport.containsIgnoreCase(hashtag.name, keyword)
                )
                .distinct()
                .limit(limit)
                .fetch();

        return new LinkedHashSet<>(productIds);
    }

    @Override
    public Set<UUID> findProductIdsByCategoryKeyword(String keyword, long limit) {
        if (keyword == null || keyword.isBlank() || limit < 1) {
            return Set.of();
        }

        List<UUID> productIds = queryFactory
                .select(categoryProduct.productId)
                .from(categoryProduct)
                .join(categoryProduct.category, category)
                .where(
                        categoryProduct.deletedAt.isNull(),
                        category.deletedAt.isNull(),
                        category.status.eq(CategoryStatus.ACTIVE),
                        QuerydslQuerySupport.containsIgnoreCase(category.name, keyword)
                )
                .distinct()
                .limit(limit)
                .fetch();

        return new LinkedHashSet<>(productIds);
    }

    @Override
    public List<ProductMetadataQueryPort.HashtagInfo> findHashtagsByProductId(UUID productId) {
        return queryFactory
                .select(Projections.constructor(
                        ProductMetadataQueryPort.HashtagInfo.class, hashtag.id, hashtag.name))
                .from(hashtagProduct)
                .join(hashtagProduct.hashtag, hashtag)
                .where(
                        hashtagProduct.productId.eq(productId),
                        hashtagProduct.deletedAt.isNull(),
                        hashtag.deletedAt.isNull()
                )
                .fetch();
    }

    @Override
    public List<ProductMetadataQueryPort.CategoryInfo> findCategoriesByProductId(UUID productId) {
        return queryFactory
                .select(Projections.constructor(
                        ProductMetadataQueryPort.CategoryInfo.class, category.id, category.name))
                .from(categoryProduct)
                .join(categoryProduct.category, category)
                .where(
                        categoryProduct.productId.eq(productId),
                        categoryProduct.deletedAt.isNull(),
                        category.deletedAt.isNull(),
                        category.status.eq(CategoryStatus.ACTIVE)
                )
                .fetch();
    }
}
