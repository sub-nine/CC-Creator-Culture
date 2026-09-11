package com.sub9.productservice.product.application.command.service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.product.application.command.dto.product.UploadImageCommand;
import com.sub9.productservice.product.application.event.ProductImageUploadedEvent;
import com.sub9.productservice.product.application.port.in.image.ProductImageCommandUseCase;
import com.sub9.productservice.product.application.port.out.image.ImageData;
import com.sub9.productservice.product.application.port.out.image.ImageProcessorPort;
import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import com.sub9.productservice.product.application.support.ImageStorageRollbackCleaner;
import com.sub9.productservice.product.application.validation.ImageValidator;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.Image;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.repository.ImageCommandRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.sub9.productservice.product.domain.repository.ProductCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductImageCommandService implements ProductImageCommandUseCase {
  private static final String ORIGINAL_KEY_FORMAT = "products/%s/images/original/%s";
  private static final String PROCESSED_KEY_FORMAT = "products/%s/images/processed/%s";

  private final ImageStorageRollbackCleaner imageStorageRollbackCleaner;
  private final ImageCommandRepository imageCommandRepository;
  private final ProductCommandRepository productCommandRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ImageProcessorPort imageProcessorPort;
  private final ImageStoragePort imageStoragePort;

  public void uploadImages(UUID productId, List<UploadImageCommand> images) {
    if (CollectionUtils.isEmpty(images)) return;

    List<String> uploadedKeys = new ArrayList<>();
    imageStorageRollbackCleaner.registerRollbackCleanup(uploadedKeys);

    List<String> mediaTypes =
        images.stream().map(image -> ImageValidator.resolveMediaType(image.data())).toList();

    for (int sortOrder = 0; sortOrder < images.size(); sortOrder++) {
      UploadImageCommand command = images.get(sortOrder);

      String mediaType = mediaTypes.get(sortOrder);

      UUID imageId = UuidCreator.getTimeOrderedEpoch();
      String originalKey = ORIGINAL_KEY_FORMAT.formatted(productId, imageId);

      Image image =
          imageCommandRepository.save(Image.create(productId, originalKey, null, sortOrder));

      imageStoragePort.upload(originalKey, new ImageData(mediaType, command.data()));

      uploadedKeys.add(originalKey);

      eventPublisher.publishEvent(
          new ProductImageUploadedEvent(
              UuidCreator.getTimeOrderedEpoch(),
              image.getId(),
              image.getProductId(),
              image.getOriginalKey()));
    }
  }



  public void deleteAllImages(UUID productId) {
    List<Image> images = imageCommandRepository.findAllByProductIdAndDeletedAtIsNull(productId);
    imageCommandRepository.deleteAll(images);
  }

  // 트랜잭션 오래 점유할 수도 있을 것 같음
  public void deleteExpiredImages(Instant now) {
    Instant cutoff = now.minus(7, ChronoUnit.DAYS);

    List<Image> expiredImages = imageCommandRepository.findExpiredImages(cutoff);

    for (Image image : expiredImages) {
      try {
        imageStoragePort.delete(image.getProcessedKey());

        if (image.getOriginalKey() != null) {
          imageStoragePort.delete(image.getOriginalKey());
        }

        imageCommandRepository.hardDeleteById(image.getId());
      } catch (Exception e) {
        log.warn("[WARN] 만료 이미지 정리 실패, imageId = {}", image.getId(), e);
      }
    }
  }

  // ============================== Helper Method ====================================
  private Product findByProductId(UUID productId) {
    return productCommandRepository
        .findByIdForUpdate(productId)
        .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
  }

}
