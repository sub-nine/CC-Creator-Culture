package com.sub9.productservice.review.presentaion.query.dto;

import com.sub9.productservice.review.application.query.dto.ReviewInfo;
import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
    UUID reviewId, UUID productId, UUID userId, int rating, String content, Instant createdAt) {
  public static ReviewResponse from(ReviewInfo info) {
    return new ReviewResponse(
        info.reviewId(), info.productId(), info.userId(), info.rating(), info.content(), info.createdAt());
  }
}
