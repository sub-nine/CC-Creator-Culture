package com.sub9.productservice.product.application.command.service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.productservice.product.application.command.dto.product.UploadImageCommand;
import com.sub9.productservice.product.application.event.ProductImageUploadedEvent;
import com.sub9.productservice.product.application.port.ImageData;
import com.sub9.productservice.product.application.port.ImageProcessorPort;
import com.sub9.productservice.product.application.port.ImageStoragePort;
import com.sub9.productservice.product.application.validation.ImageValidator;
import com.sub9.productservice.product.domain.model.Image;
import com.sub9.productservice.product.domain.repository.ImageCommandRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
public class ProductImageCommandService {
  private static final String ORIGINAL_KEY_FORMAT = "products/%s/images/original/%s";
  private static final String PROCESSED_KEY_FORMAT = "products/%s/images/processed/%s";

  private final ImageCommandRepository imageCommandRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ImageProcessorPort imageProcessorPort;
  private final ImageStoragePort imageStoragePort;

  public void uploadImages(UUID productId, List<UploadImageCommand> images) {
    if (CollectionUtils.isEmpty(images)) return;

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

      eventPublisher.publishEvent(
          new ProductImageUploadedEvent(
              UuidCreator.getTimeOrderedEpoch(),
              image.getId(),
              image.getProductId(),
              image.getOriginalKey()));
    }
  }

  // TODO : 리사이징 실패 시 재시도 또는 처리 로직 필요
  public void resizeImage(UUID imageId, UUID productId, String originalKey) {
    String processedKey = PROCESSED_KEY_FORMAT.formatted(productId, imageId);

    ImageData originalImageData = imageStoragePort.download(originalKey);
    ImageData resizedImageData = imageProcessorPort.resize(originalImageData);

    imageStoragePort.upload(processedKey, resizedImageData);

    boolean updated = imageCommandRepository.completeProcessing(imageId, processedKey);

    if (!updated) {
      log.warn("[FAIL] 리사이징 이미지 업데이트 실패 imageId={}, processedKey={}", imageId, processedKey);
    }
  }

  public void deleteAllImages(UUID productId) {
    List<Image> images = imageCommandRepository.findAllByProductIdAndDeletedAtIsNull(productId);
    for (Image image : images) image.delete();
  }

  public void deleteExpiredImages(Instant instant) {
    // TODO : 추후 DeletedAt 기준 일주일이 지나면 스케쥴러로 삭제처리
    throw new UnsupportedOperationException("개발 중 입니다.");
  }
}
