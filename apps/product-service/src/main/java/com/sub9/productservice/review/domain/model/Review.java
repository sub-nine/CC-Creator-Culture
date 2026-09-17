package com.sub9.productservice.review.domain.model;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.common.entity.BaseEntity;
import com.sub9.productservice.review.domain.exception.ReviewErrorCode;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "p_reviews",
    indexes = {
      @Index(name = "idx_reviews_product_id_created_at", columnList = "product_id, created_at")
    })
public class Review extends BaseEntity {
  @Column(nullable = false)
  private UUID orderItemId;

  @Column(nullable = false)
  private UUID productId;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private int rating;

  @Column(length = 1000)
  private String content;

  public static Review create(
      UUID orderItemId, UUID productId, UUID userId, int rating, String content) {
    validateRating(rating);

    Review review = new Review();
    review.orderItemId = orderItemId;
    review.productId = productId;
    review.userId = userId;
    review.rating = rating;
    review.content = content;
    return review;
  }

  public void update(int rating, String content) {
    validateRating(rating);

    this.rating = rating;
    this.content = content;
  }

  private static void validateRating(int rating) {
    if (rating < 1 || rating > 5) {
      throw new BusinessException(ReviewErrorCode.INVALID_RATTING);
    }
  }
}
