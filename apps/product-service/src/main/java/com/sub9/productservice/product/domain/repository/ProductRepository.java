package com.sub9.productservice.product.domain.repository;

import com.sub9.productservice.product.domain.model.Product;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
  Product save(Product product);

  Optional<Product> findByIdAndDeletedAtIsNull(UUID productId);

  Optional<Product> findByIdForUpdate(UUID productId);

  void incrementViewCount(UUID productId, long viewCount);

  void addReviewStats(UUID productId, int rating);

  void removeReviewStats(UUID productId, int rating);

  void updateReviewRating(UUID productId, int oldRating, int newRating);
}
