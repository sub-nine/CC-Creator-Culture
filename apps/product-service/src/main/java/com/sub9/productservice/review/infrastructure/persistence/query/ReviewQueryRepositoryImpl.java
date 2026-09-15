package com.sub9.productservice.review.infrastructure.persistence.query;

import static com.sub9.productservice.review.domain.model.QReview.review;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sub9.productservice.review.application.port.out.ReviewQueryRepository;
import com.sub9.productservice.review.application.query.dto.ReviewInfo;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReviewQueryRepositoryImpl implements ReviewQueryRepository {
  private final JPAQueryFactory queryFactory;

  @Override
  public Slice<ReviewInfo> findAllByProductId(UUID productId, Pageable pageable) {
    return findReviews(review.productId.eq(productId), pageable);
  }

  @Override
  public Slice<ReviewInfo> findAllByUserId(UUID userId, Pageable pageable) {
    return findReviews(review.userId.eq(userId), pageable);
  }

  // ============================== Helper Method ====================================
  private Slice<ReviewInfo> findReviews(BooleanExpression condition, Pageable pageable) {
    int size = pageable.getPageSize();
    List<ReviewInfo> result =
        queryFactory
            .select(
                Projections.constructor(
                    ReviewInfo.class,
                    review.id,
                    review.productId,
                    review.userId,
                    review.rating,
                    review.content,
                    review.createdAt))
            .from(review)
            .where(condition, review.deletedAt.isNull())
            .orderBy(review.createdAt.desc(), review.id.desc())
            .offset(pageable.getOffset())
            .limit(size + 1)
            .fetch();

    boolean hasNext = result.size() > size;
    return new SliceImpl<>(hasNext ? result.subList(0, size) : result, pageable, hasNext);
  }
}
