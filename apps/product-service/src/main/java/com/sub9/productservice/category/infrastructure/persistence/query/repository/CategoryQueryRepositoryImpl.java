package com.sub9.productservice.category.infrastructure.persistence.query.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sub9.productservice.category.application.query.port.out.CategoryQueryRepository;
import com.sub9.productservice.category.domain.entity.QCategory;
import com.sub9.productservice.category.domain.entity.QCategoryHashtag;
import com.sub9.productservice.category.domain.entity.QHashtag;
import com.sub9.productservice.category.domain.entity.QHashtagProduct;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.category.domain.model.CategoryStatus;
import com.sub9.productservice.category.infrastructure.persistence.query.support.QuerydslQuerySupport;
import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import com.sub9.productservice.category.presentation.query.dto.ProductCategoryIdsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CategoryQueryRepositoryImpl implements CategoryQueryRepository {

    private static final QCategory category = QCategory.category;
    private static final QCategoryHashtag categoryHashtag = QCategoryHashtag.categoryHashtag;
    private static final QHashtag hashtag = QHashtag.hashtag;
    private static final QHashtagProduct hashtagProduct = QHashtagProduct.hashtagProduct;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<CategoryResponse> searchCategories(String keyword, Pageable pageable) {
        List<CategoryResponse> content = queryFactory
                .select(Projections.constructor(CategoryResponse.class,
                        category.id, category.name, category.description))
                .from(category)
                .where(
                        category.deletedAt.isNull(),
                        category.status.eq(CategoryStatus.ACTIVE),
                        QuerydslQuerySupport.containsIgnoreCase(category.name, keyword)
                )
                .orderBy(QuerydslQuerySupport.orderSpecifiers(category, pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(category.count())
                .from(category)
                .where(
                        category.deletedAt.isNull(),
                        category.status.eq(CategoryStatus.ACTIVE),
                        QuerydslQuerySupport.containsIgnoreCase(category.name, keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Optional<CategoryResponse> findById(UUID categoryId) {
        CategoryResponse result = queryFactory
                .select(Projections.constructor(CategoryResponse.class,
                        category.id, category.name, category.description))
                .from(category)
                .where(
                        category.id.eq(categoryId),
                        category.deletedAt.isNull(),
                        category.status.eq(CategoryStatus.ACTIVE)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<CategoryResponse> searchCategoriesByIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .select(Projections.constructor(CategoryResponse.class,
                        category.id, category.name, category.description))
                .from(category)
                .where(
                        category.id.in(ids),
                        category.deletedAt.isNull(),
                        category.status.eq(CategoryStatus.ACTIVE)
                )
                .fetch();
    }

    @Override
    public List<HashtagResponse> findHashtagsByCategoryId(UUID categoryId) {
        return queryFactory
                .select(Projections.constructor(HashtagResponse.class, hashtag.id, hashtag.name, hashtag.usageCount))
                .from(categoryHashtag)
                .join(categoryHashtag.hashtag, hashtag)
                .where(mergedInCategory(categoryId))
                .orderBy(hashtag.createdAt.desc())
                .fetch();
    }

    @Override
    public Page<HashtagResponse> findHashtagsByCategoryId(UUID categoryId, Pageable pageable) {
        List<HashtagResponse> content = queryFactory
                .select(Projections.constructor(HashtagResponse.class, hashtag.id, hashtag.name, hashtag.usageCount))
                .from(categoryHashtag)
                .join(categoryHashtag.hashtag, hashtag)
                .where(mergedInCategory(categoryId))
                .orderBy(QuerydslQuerySupport.orderSpecifiers(hashtag, pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(categoryHashtag.count())
                .from(categoryHashtag)
                .join(categoryHashtag.hashtag, hashtag)
                .where(mergedInCategory(categoryId));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public List<ProductCategoryIdsResponse> findCategoryIdsByProductIds(List<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }

        // p_categories_products를 직접 유지하는 대신, HashtagProduct와 MERGED된 CategoryHashtag를 조인해서 파생시킨다
        // QueryDSL의 GroupBy.transform()은 이 프로젝트의 Hibernate 버전과 바이너리 호환이 안 돼서(ScrollableResults API 변경)
        // 직접 fetch한 뒤 자바 스트림으로 그룹핑한다
        List<Tuple> rows = queryFactory
                .select(hashtagProduct.productId, categoryHashtag.category.id)
                .from(hashtagProduct)
                .join(categoryHashtag).on(categoryHashtag.hashtag.eq(hashtagProduct.hashtag))
                .where(
                        hashtagProduct.productId.in(productIds),
                        hashtagProduct.deletedAt.isNull(),
                        categoryHashtag.status.eq(CategoryHashtagStatus.MERGED),
                        categoryHashtag.deletedAt.isNull(),
                        categoryHashtag.category.deletedAt.isNull(),
                        categoryHashtag.category.status.eq(CategoryStatus.ACTIVE)
                )
                .distinct()
                .fetch();

        Map<UUID, List<UUID>> categoryIdsByProductId = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> row.get(hashtagProduct.productId),
                        Collectors.mapping(row -> row.get(categoryHashtag.category.id), Collectors.toList())
                ));

        return categoryIdsByProductId.entrySet().stream()
                .map(entry -> new ProductCategoryIdsResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private BooleanExpression mergedInCategory(UUID categoryId) {
        return categoryHashtag.category.id.eq(categoryId)
                .and(categoryHashtag.status.eq(CategoryHashtagStatus.MERGED))
                .and(categoryHashtag.deletedAt.isNull())
                .and(hashtag.deletedAt.isNull());
    }
}
