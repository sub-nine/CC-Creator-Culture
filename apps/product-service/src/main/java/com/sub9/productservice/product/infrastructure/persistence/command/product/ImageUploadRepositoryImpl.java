package com.sub9.productservice.product.infrastructure.persistence.command.product;

import com.sub9.productservice.product.domain.model.ImageUpload;
import com.sub9.productservice.product.domain.repository.ImageUploadRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ImageUploadRepositoryImpl implements ImageUploadRepository {
  private final ImageUploadJpaRepository jpaRepository;

  @Override
  public ImageUpload save(ImageUpload imageUpload) {
    return jpaRepository.save(imageUpload);
  }

  @Override
  public Optional<ImageUpload> findById(UUID uploadId) {
    return jpaRepository.findById(uploadId);
  }

  @Override
  public void delete(ImageUpload imageUpload) {
    jpaRepository.delete(imageUpload);
  }

  @Override
  public List<ImageUpload> findAllByCreatedAtBefore(Instant cutoff) {
    return jpaRepository.findAllByCreatedAtBefore(cutoff);
  }
}
