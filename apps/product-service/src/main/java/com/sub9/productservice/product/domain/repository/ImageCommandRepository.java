package com.sub9.productservice.product.domain.repository;

import com.sub9.productservice.product.domain.model.Image;

import java.util.List;
import java.util.UUID;

public interface ImageCommandRepository {
  Image save(Image savedOriginalImage);

  boolean completeProcessing(UUID imageId, String processedKey);

  List<Image> findAllByProductIdAndDeletedAtIsNull(UUID productId);
}
