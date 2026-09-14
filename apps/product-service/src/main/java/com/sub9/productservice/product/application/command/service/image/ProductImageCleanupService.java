package com.sub9.productservice.product.application.command.service.image;

import com.sub9.productservice.product.application.port.in.image.ProductImageCleanupUseCase;
import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import com.sub9.productservice.product.domain.model.Image;
import com.sub9.productservice.product.domain.repository.ImageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductImageCleanupService implements ProductImageCleanupUseCase {
  private final ImageRepository imageRepository;
  private final ImageStoragePort imageStoragePort;

  // 트랜잭션 오래 점유할 수도 있을 것 같음
  public void deleteExpiredImages(Instant now) {
    Instant cutoff = now.minus(7, ChronoUnit.DAYS);

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
}
