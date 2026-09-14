package com.sub9.productservice.review.infrastructure.persistence.command;

import com.sub9.productservice.review.domain.model.Review;
import com.sub9.productservice.review.domain.repository.ReviewRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepository {
  private final ReviewJpaRepository jpaRepository;

  @Override
  public Review save(Review review) {
    return jpaRepository.save(review);
  }

  @Override
  public boolean existsByOrderItemIdAndDeletedAtIsNull(UUID orderItemId) {
    return jpaRepository.existsByOrderItemIdAndDeletedAtIsNull(orderItemId);
  }
}
