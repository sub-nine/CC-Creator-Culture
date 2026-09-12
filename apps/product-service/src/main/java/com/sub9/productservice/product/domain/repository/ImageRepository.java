package com.sub9.productservice.product.domain.repository;

import com.sub9.productservice.product.domain.model.Image;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ImageRepository {
  Image save(Image savedOriginalImage);

  boolean completeProcessing(UUID imageId, String processedKey);

  List<Image> findAllByProductIdAndDeletedAtIsNull(UUID productId);

  void deleteAll(List<Image> images);

  List<Image> findExpiredImages(Instant cutoff);

  void hardDeleteById(UUID imageId);

  boolean softDelete(UUID imageId, UUID productId, UUID creatorId);
}
