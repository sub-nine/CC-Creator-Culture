package com.sub9.productservice.category.infrastructure.persistence.query.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sub9.productservice.category.application.query.port.out.HashtagQueryRepository;
import com.sub9.productservice.category.domain.entity.QHashtag;
import com.sub9.productservice.category.domain.entity.QHashtagProduct;
import com.sub9.productservice.category.infrastructure.persistence.query.support.QuerydslQuerySupport;
import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import com.sub9.productservice.category.presentation.query.dto.ProductHashtagIdsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.querydsl.core.group.GroupBy.groupBy;
import static com.querydsl.core.group.GroupBy.list;

@Repository
@RequiredArgsConstructor
public class HashtagQueryRepositoryImpl implements HashtagQueryRepository {

    private static final QHashtag hashtag = QHashtag.hashtag;
    private static final QHashtagProduct hashtagProduct = QHashtagProduct.hashtagProduct;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<HashtagResponse> searchHashtags(String keyword, Pageable pageable) {
        List<HashtagResponse> content = queryFactory
                .select(Projections.constructor(HashtagResponse.class,
                        hashtag.id, hashtag.name, hashtag.usageCount))
                .from(hashtag)
                .where(
                        hashtag.deletedAt.isNull(),
                        QuerydslQuerySupport.containsIgnoreCase(hashtag.name, keyword)
                )
                .orderBy(QuerydslQuerySupport.orderSpecifiers(hashtag, pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(hashtag.count())
                .from(hashtag)
                .where(
                        hashtag.deletedAt.isNull(),
                        QuerydslQuerySupport.containsIgnoreCase(hashtag.name, keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public List<HashtagResponse> searchHashtagsByIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .select(Projections.constructor(HashtagResponse.class,
                        hashtag.id, hashtag.name, hashtag.usageCount))
                .from(hashtag)
                .where(
                        hashtag.id.in(ids),
                        hashtag.deletedAt.isNull()
                )
                .fetch();
    }

    @Override
    public List<ProductHashtagIdsResponse> findHashtagIdsByProductIds(List<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<UUID>> hashtagIdsByProductId = queryFactory
                .from(hashtagProduct)
                .where(
                        hashtagProduct.productId.in(productIds),
                        hashtagProduct.deletedAt.isNull()
                )
                .transform(groupBy(hashtagProduct.productId).as(list(hashtagProduct.hashtag.id)));

        return hashtagIdsByProductId.entrySet().stream()
                .map(entry -> new ProductHashtagIdsResponse(entry.getKey(), entry.getValue()))
                .toList();
    }
}
