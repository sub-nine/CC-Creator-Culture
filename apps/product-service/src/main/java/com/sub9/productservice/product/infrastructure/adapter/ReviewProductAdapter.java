package com.sub9.productservice.product.infrastructure.adapter;

import com.sub9.productservice.product.domain.repository.ProductRepository;
import com.sub9.productservice.review.application.port.out.ReviewProductPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewProductAdapter implements ReviewProductPort {
  private final ProductRepository productRepository;

  @Override
  public void addReviewStats(UUID productId, int rating) {
    productRepository.addReviewStats(productId, rating);
  }

  @Override
  public void removeReviewStats(UUID productId, int rating) {
    productRepository.removeReviewStats(productId, rating);
  }

  @Override
  public void updateReviewRating(UUID productId, int oldRating, int newRating) {
    productRepository.updateReviewRating(productId, oldRating, newRating);
  }
}
