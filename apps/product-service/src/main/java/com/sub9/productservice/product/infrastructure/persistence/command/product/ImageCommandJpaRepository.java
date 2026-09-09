package com.sub9.productservice.product.infrastructure.persistence.command.product;

import com.sub9.productservice.product.domain.model.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
}
