package com.sub9.productservice.review.domain.repository;

import com.sub9.productservice.review.domain.model.Review;
import java.util.UUID;

public interface ReviewRepository {
  Review save(Review review);

  boolean existsByOrderItemIdAndDeletedAtIsNull(UUID orderItemId);
}
