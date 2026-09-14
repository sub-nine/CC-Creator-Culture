package com.sub9.productservice.product.infrastructure.persistence.command.product;

import com.sub9.productservice.product.domain.model.Image;
import com.sub9.productservice.product.domain.repository.ImageCommandRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ImageCommandRepositoryImpl implements ImageCommandRepository {
  private final ImageCommandJpaRepository jpaRepository;

  @Override
  public Image save(Image image) {
    return jpaRepository.save(image);
  }

  @Override
  public boolean completeProcessing(UUID imageId, String processedKey) {
    return jpaRepository.completeProcessing(imageId, processedKey, Instant.now()) > 0;
  }

  @Override
  public List<Image> findAllByProductIdAndDeletedAtIsNull(UUID productId) {
    return jpaRepository.findAllByProductIdAndDeletedAtIsNull(productId);
  }

  @Override
  public void deleteAll(List<Image> images) {
    jpaRepository.deleteAll(images);
  }

  @Override
  public List<Image> findExpiredImages(Instant cutoff) {
    return jpaRepository.findCleanUpTargets(cutoff);
  }

  @Override
  public void hardDeleteById(UUID imageId) {
    jpaRepository.hardDeleteById(imageId);
  }

  @Override
  public boolean softDelete(UUID imageId, UUID productId, UUID creatorId) {
    return jpaRepository.softDelete(imageId, productId, creatorId) > 0;
  }
}

