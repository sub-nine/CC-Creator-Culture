package com.sub9.productservice.category.infrastructure.persistence.query.support;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.StringPath;
import org.springframework.data.domain.Sort;

public final class QuerydslQuerySupport {

    private QuerydslQuerySupport() {
    }

    public static BooleanExpression containsIgnoreCase(StringPath path, String keyword) {
        return (keyword == null || keyword.isBlank()) ? null : path.containsIgnoreCase(keyword);
    }

    // TODO: 정렬 프로퍼티 화이트리스트 검증 추가 필요.
    // 지금은 존재하지 않는 프로퍼티로 sort 요청이 오면 PathBuilder가 경로를 그대로 생성해서
    // QueryDSL/Hibernate 조회 시점에 예외가 터져 400이 아닌 500으로 응답됨.
    public static <T> OrderSpecifier<?>[] orderSpecifiers(EntityPathBase<T> entityPath, Sort sort) {
        PathBuilder<T> pathBuilder = new PathBuilder<>(entityPath.getType(), entityPath.getMetadata());

        return sort.stream()
                .map(order -> new OrderSpecifier<>(
                        order.isAscending() ? Order.ASC : Order.DESC,
                        pathBuilder.getComparable(order.getProperty(), Comparable.class)
                ))
                .toArray(OrderSpecifier[]::new);
    }
}
