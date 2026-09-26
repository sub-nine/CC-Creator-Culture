package com.sub9.productservice.product.application.command.service.image;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.product.application.port.in.image.ProductImageProcessingUseCase;
import com.sub9.productservice.product.application.port.out.image.ImageData;
import com.sub9.productservice.product.application.port.out.image.ImageProcessorPort;
import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import com.sub9.productservice.product.application.support.ImageStorageRollbackCleaner;
import com.sub9.productservice.product.application.validation.ImageValidator;
import com.sub9.productservice.product.domain.repository.ImageRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductImageProcessingService implements ProductImageProcessingUseCase {
  private static final String PROCESSED_KEY_FORMAT = "products/images/processed/%s.%s.jpg";
  private final ImageStorageRollbackCleaner imageStorageRollbackCleaner;
  private final ImageRepository imageRepository;
  private final ImageProcessorPort imageProcessorPort;
  private final ImageStoragePort imageStoragePort;

  public void resizeImage(UUID imageId, UUID productId, String originalKey) {
    String processedKey = PROCESSED_KEY_FORMAT.formatted(productId, imageId);

    List<String> uploadedKeys = new ArrayList<>();
    imageStorageRollbackCleaner.registerRollbackCleanup(uploadedKeys);

    ImageData originalImageData = imageStoragePort.download(originalKey);

    try { // 이미지 검증 실패 시 원본 데이터 삭제
      ImageValidator.resolveMediaType(originalImageData.data());
    } catch (BusinessException e) {
      imageStoragePort.delete(originalKey);
      imageRepository.hardDeleteById(imageId);
      return;
    }

    ImageData resizedImageData = imageProcessorPort.resize(originalImageData);

    imageStoragePort.upload(processedKey, resizedImageData);
    uploadedKeys.add(processedKey);

    boolean updated = imageRepository.completeProcessing(imageId, processedKey);

    if (!updated) {
      log.warn("[FAIL] 리사이징 이미지 업데이트 실패 imageId= {}, processedKey = {}", imageId, processedKey);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
