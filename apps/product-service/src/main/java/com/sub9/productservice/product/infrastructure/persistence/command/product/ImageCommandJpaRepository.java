package com.sub9.productservice.product.infrastructure.persistence.command.product;

import com.sub9.productservice.product.domain.model.Image;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ImageCommandJpaRepository extends JpaRepository<Image, UUID> {

  @Modifying
  @Query(
      """
   UPDATE Image i
   SET i.processedKey = :processedKey,
       i.status = 'COMPLETED',
       i.processedAt = :processedAt
   WHERE i.id = :imageId
   AND i.status = 'PENDING'
   AND i.deletedAt IS NULL
   """)
  int completeProcessing(UUID imageId, String processedKey, Instant processedAt);

  List<Image> findAllByProductIdAndDeletedAtIsNull(UUID productId);

  @Modifying
  @Query("DELETE FROM Image i WHERE i.id = :imageId")
  void hardDeleteById(UUID imageId);

  @Modifying
  @Query(
      """
  UPDATE Image i
  SET i.deletedAt = CURRENT_TIMESTAMP
  WHERE i.id = :imageId
  AND i.deletedAt IS NULL
  AND i.productId = :productId
  AND EXISTS (
       SELECT 1
       FROM Product p
       WHERE p.id = i.productId
       AND p.creatorId = :creatorId)
  """)
  int softDelete(UUID imageId, UUID productId, UUID creatorId);

  @Query(
      """
  SELECT i
  FROM Image i
  WHERE i.deletedAt <= :cutoff
  OR EXISTS (
      SELECT 1
      FROM Product p
      WHERE p.id = i.productId
      AND p.deletedAt <= :cutoff)
  """)
  List<Image> findCleanUpTargets(Instant cutoff);
}
