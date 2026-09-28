package com.sub9.productservice.product.application.command.service.image;

import com.sub9.productservice.product.application.port.in.image.ProductImageCleanupUseCase;
import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import com.sub9.productservice.product.domain.model.Image;
import com.sub9.productservice.product.domain.model.ImageUpload;
import com.sub9.productservice.product.domain.repository.ImageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.sub9.productservice.product.domain.repository.ImageUploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductImageCleanupService implements ProductImageCleanupUseCase {
  private static final long IMAGE_RETENTION_DAYS = 7L;
  private static final long UPLOAD_RETENTION_HOURS = 1L;

  private final ImageUploadRepository imageUploadRepository;
  private final ImageStoragePort imageStoragePort;
  private final ImageRepository imageRepository;

  public void deleteExpiredImages(Instant now) {
    Instant cutoff = now.minus(IMAGE_RETENTION_DAYS, ChronoUnit.DAYS);

    List<Image> expiredImages = imageRepository.findExpiredImages(cutoff);

    for (Image image : expiredImages) {
      try {
        if (image.getOriginalKey() != null) imageStoragePort.delete(image.getOriginalKey());
        if (image.getProcessedKey() != null) imageStoragePort.delete(image.getProcessedKey());

        imageRepository.hardDeleteById(image.getId());
      } catch (Exception e) {
        log.warn("[WARN] 만료 이미지 정리 실패, imageId = {}", image.getId(), e);
      }
    }
  }

  @Override
  public void deleteExpiredImageUploads(Instant now) {
    Instant cutoff = now.minus(UPLOAD_RETENTION_HOURS, ChronoUnit.HOURS);

    List<ImageUpload> expiredUploads =
        imageUploadRepository.findAllByCreatedAtBefore(cutoff);

    for (ImageUpload uploadImage : expiredUploads) {
      try {
        imageStoragePort.delete(uploadImage.getObjectKey());
        imageUploadRepository.delete(uploadImage);

      } catch (Exception e) {
        log.warn("[WARN] 미완료 업로드 이미지 정리 실패, objectKey = {}", uploadImage.getObjectKey(), e);
      }
    }
  }
}
