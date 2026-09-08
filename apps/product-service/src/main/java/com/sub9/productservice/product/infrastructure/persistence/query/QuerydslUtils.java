package com.sub9.productservice.product.infrastructure.persistence.query;

import com.querydsl.core.types.dsl.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

/** QueryDSL 유틸리티 클래스 */
@UtilityClass
public class QuerydslUtils {

  /** 단일 값 비교 */
  public static <T> BooleanExpression eq(SimpleExpression<T> data, T value) {
    return value == null ? null : data.eq(value);
  }

  /** 여러 값 비교 */
  public static <V> BooleanExpression in(SimpleExpression<V> data, Collection<? extends V> values) {
    return (values == null || values.isEmpty()) ? null : data.in(values);
  }

  /** Collection / String 값 비교 */
  public static <V> BooleanExpression contains(CollectionPathBase<?, V, ?> data, V value) {
    return value == null ? null : data.contains(value);
  }

  public static BooleanExpression containsIgnoreCase(StringPath data, String value) {
    return StringUtils.hasText(value) ? data.containsIgnoreCase(value) : null;
  }

  public static BooleanExpression startsWith(StringPath data, String value) {
    return StringUtils.hasText(value) ? data.startsWith(value) : null;
  }

  /** 검색 기간 */
  public static BooleanExpression createdAtBetween(
      DateTimePath<LocalDateTime> data, LocalDate startDate, LocalDate endDate) {
    if (startDate != null && endDate != null) {
      return data.between(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX));
    }

    if (startDate != null) {
      return data.goe(startDate.atStartOfDay());
    }

    if (endDate != null) {
      return data.loe(endDate.atTime(LocalTime.MAX));
    }
    return null;
  }
}
