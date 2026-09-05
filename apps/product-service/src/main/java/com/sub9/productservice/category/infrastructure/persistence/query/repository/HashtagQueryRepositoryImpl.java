package com.sub9.productservice.category.infrastructure.persistence.query.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sub9.productservice.category.application.query.repository.HashtagQueryRepository;
import com.sub9.productservice.category.domain.entity.QHashtag;
import com.sub9.productservice.category.infrastructure.persistence.query.support.QuerydslQuerySupport;
import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class HashtagQueryRepositoryImpl implements HashtagQueryRepository {

    private static final QHashtag hashtag = QHashtag.hashtag;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<HashtagResponse> searchHashtags(String keyword, Pageable pageable) {
        List<HashtagResponse> content = queryFactory
                .select(Projections.constructor(HashtagResponse.class,
                        hashtag.id, hashtag.name))
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
}
