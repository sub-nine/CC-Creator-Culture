package com.sub9.productservice.category.infrastructure.persistence.query.repository;

import com.querydsl.core.Tuple;
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
import java.util.stream.Collectors;

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

        // QueryDSL의 GroupBy.transform()은 이 프로젝트의 Hibernate 버전과 바이너리 호환이 안 돼서(ScrollableResults API 변경)
        // 직접 fetch한 뒤 자바 스트림으로 그룹핑한다
        List<Tuple> rows = queryFactory
                .select(hashtagProduct.productId, hashtagProduct.hashtag.id)
                .from(hashtagProduct)
                .where(
                        hashtagProduct.productId.in(productIds),
                        hashtagProduct.deletedAt.isNull()
                )
                .fetch();

        Map<UUID, List<UUID>> hashtagIdsByProductId = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> row.get(hashtagProduct.productId),
                        Collectors.mapping(row -> row.get(hashtagProduct.hashtag.id), Collectors.toList())
                ));

        return hashtagIdsByProductId.entrySet().stream()
                .map(entry -> new ProductHashtagIdsResponse(entry.getKey(), entry.getValue()))
                .toList();
    }
}
