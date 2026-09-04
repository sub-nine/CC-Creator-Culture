package com.sub9.productservice.category.infrastructure.persistence.query.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sub9.productservice.category.application.query.repository.CategoryQueryRepository;
import com.sub9.productservice.category.domain.entity.QCategory;
import com.sub9.productservice.category.domain.model.CategoryStatus;
import com.sub9.productservice.category.infrastructure.persistence.query.support.QuerydslQuerySupport;
import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CategoryQueryRepositoryImpl implements CategoryQueryRepository {

    private static final QCategory category = QCategory.category;

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
}
