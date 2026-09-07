package com.sub9.productservice.category.infrastructure.persistence.query.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sub9.productservice.category.application.query.port.out.MergeRequestQueryRepository;
import com.sub9.productservice.category.domain.entity.QCategory;
import com.sub9.productservice.category.domain.entity.QCategoryHashtag;
import com.sub9.productservice.category.domain.entity.QHashtag;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.category.infrastructure.persistence.query.support.QuerydslQuerySupport;
import com.sub9.productservice.category.presentation.query.dto.MergeRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MergeRequestQueryRepositoryImpl implements MergeRequestQueryRepository {

    private static final QCategoryHashtag categoryHashtag = QCategoryHashtag.categoryHashtag;
    private static final QCategory category = QCategory.category;
    private static final QHashtag hashtag = QHashtag.hashtag;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<MergeRequestResponse> findPendingMergeRequests(Pageable pageable) {
        List<MergeRequestResponse> content = queryFactory
                .select(Projections.constructor(MergeRequestResponse.class,
                        categoryHashtag.id,
                        category.id,
                        category.name,
                        hashtag.id,
                        hashtag.name,
                        categoryHashtag.status.stringValue(),
                        categoryHashtag.createdAt))
                .from(categoryHashtag)
                .join(categoryHashtag.category, category)
                .join(categoryHashtag.hashtag, hashtag)
                .where(pendingApproval())
                .orderBy(QuerydslQuerySupport.orderSpecifiers(categoryHashtag, pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(categoryHashtag.count())
                .from(categoryHashtag)
                .join(categoryHashtag.category, category)
                .join(categoryHashtag.hashtag, hashtag)
                .where(pendingApproval());

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression pendingApproval() {
        return categoryHashtag.status.eq(CategoryHashtagStatus.PENDING_APPROVAL)
                .and(categoryHashtag.deletedAt.isNull())
                .and(category.deletedAt.isNull())
                .and(hashtag.deletedAt.isNull());
    }
}
