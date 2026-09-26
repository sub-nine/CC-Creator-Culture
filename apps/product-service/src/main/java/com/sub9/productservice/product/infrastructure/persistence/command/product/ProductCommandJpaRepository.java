package com.sub9.productservice.product.infrastructure.persistence.command.product;

import com.sub9.productservice.product.domain.model.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductCommandJpaRepository extends JpaRepository<Product, UUID> {
  Optional<Product> findByIdAndDeletedAtIsNull(UUID productId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
          SELECT p
          FROM Product p
          WHERE p.id = :productId
            AND p.deletedAt is null
          """)
  Optional<Product> findByIdForUpdate(UUID productId);

  @Modifying
  @Query(
      """
              UPDATE Product p
              SET p.viewCount = p.viewCount + :viewCount
              WHERE p.id = :productId
              AND p.deletedAt IS NULL
          """)
  void incrementViewCount(UUID productId, long viewCount);

  @Modifying
  @Query(
      """
    UPDATE Product p
       SET p.reviewCount = p.reviewCount + 1,
           p.ratingSum = p.ratingSum + :rating
     WHERE p.id = :productId
       AND p.deletedAt IS NULL
    """)
  void addReviewStats(UUID productId, int rating);

  @Modifying
  @Query(
      """
      UPDATE Product p
         SET p.reviewCount = p.reviewCount - 1,
             p.ratingSum = p.ratingSum - :rating
       WHERE p.id = :productId
         AND p.deletedAt IS NULL
      """)
  void removeReviewStats(UUID productId, int rating);

  @Modifying
  @Query(
      """
      UPDATE Product p
         SET p.ratingSum = p.ratingSum - :oldRating + :newRating
       WHERE p.id = :productId
         AND p.deletedAt IS NULL
      """)
  void updateReviewRating(UUID productId, int oldRating, int newRating);
}
