package com.sub9.productservice.review.application.port.out;

import java.util.UUID;

public interface ReviewProductPort {
  void addReviewStats(UUID productId, int rating);

  void removeReviewStats(UUID productId, int rating);

  void updateReviewRating(UUID productId, int oldRating, int newRating);
}
