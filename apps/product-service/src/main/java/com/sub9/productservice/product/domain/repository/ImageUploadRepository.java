package com.sub9.productservice.product.domain.repository;

import com.sub9.productservice.product.domain.model.ImageUpload;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImageUploadRepository {
  ImageUpload save(ImageUpload imageUpload);

  Optional<ImageUpload> findById(UUID uploadId);

  void delete(ImageUpload imageUpload);

  List<ImageUpload> findAllByCreatedAtBefore(Instant cutoff);
}
