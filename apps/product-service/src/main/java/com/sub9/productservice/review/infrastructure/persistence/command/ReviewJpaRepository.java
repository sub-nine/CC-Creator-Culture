package com.sub9.productservice.review.infrastructure.persistence.command;

import com.sub9.productservice.review.domain.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewJpaRepository extends JpaRepository<Review, UUID> {
  boolean existsByOrderItemIdAndDeletedAtIsNull(UUID orderItemId);
}
