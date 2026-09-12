package com.sub9.productservice.product.application.command.service.image;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.command.dto.product.DeleteProductImageCommand;
import com.sub9.productservice.product.application.command.dto.product.UpdateImageSortOrderCommand;
import com.sub9.productservice.product.application.command.dto.product.UploadImageCommand;
import com.sub9.productservice.product.application.event.ProductImageUploadedEvent;
import com.sub9.productservice.product.application.port.in.image.ProductImageCommandUseCase;
import com.sub9.productservice.product.application.port.out.image.ImageData;
import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import com.sub9.productservice.product.application.support.ImageStorageRollbackCleaner;
import com.sub9.productservice.product.application.validation.ImageValidator;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.Image;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.repository.ImageRepository;
import com.sub9.productservice.product.domain.repository.ProductRepository;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  private final ImageStorageRollbackCleaner imageStorageRollbackCleaner;
  private final ProductRepository productRepository;
  private final ImageRepository imageRepository;
  private final ApplicationEventPublisher eventPublisher;
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
          imageRepository.save(Image.create(productId, originalKey, null, sortOrder));

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

  @Override
  public void updateSortOrder(UpdateImageSortOrderCommand command) {
    validateOwner(command.productId(), command.creatorId());

    List<Image> images =
        imageRepository.findAllByProductIdAndDeletedAtIsNull(command.productId());

    Map<UUID, Image> imageMap =
        images.stream().collect(Collectors.toMap(Image::getId, Function.identity()));

    // 중복 제거
    Set<UUID> requestedImageIds = new HashSet<>(command.imageIds());

    // 실제 이미지와 개수 일치 검증 및 ID 값들이 동일한지 체크
    if (command.imageIds().size() != requestedImageIds.size()
        || !imageMap.keySet().equals(requestedImageIds)) {
      throw new BusinessException(ProductErrorCode.INVALID_PRODUCT_IMAGE_INFO);
    }

    for (int sortOrder = 0; sortOrder < command.imageIds().size(); sortOrder++) {
      Image image = imageMap.get(command.imageIds().get(sortOrder));
      image.updateSortOrder(sortOrder);
    }
  }

  @Override
  public void delete(DeleteProductImageCommand command) {
    validateOwner(command.productId(), command.creatorId());

    boolean deleted =
        imageRepository.softDelete(
            command.imageId(), command.productId(), command.creatorId());

    if (!deleted) throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_NOT_FOUND);
  }

  private void validateOwner(UUID productId, UUID creatorId) {
    Product product =
        productRepository
            .findByIdForUpdate(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    product.validateOwner(creatorId);
  }
}
