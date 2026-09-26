package com.sub9.productservice.product.application.command.service.image;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.product.application.command.dto.product.AddImagesCommand;
import com.sub9.productservice.product.application.command.dto.product.DeleteProductImageCommand;
import com.sub9.productservice.product.application.command.dto.product.UpdateImageSortOrderCommand;
import com.sub9.productservice.product.application.event.ProductImageUploadedEvent;
import com.sub9.productservice.product.application.port.in.image.ProductImageCommandUseCase;
import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import com.sub9.productservice.product.application.support.ImageStorageRollbackCleaner;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.Image;
import com.sub9.productservice.product.domain.model.ImageUpload;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.repository.ImageRepository;
import com.sub9.productservice.product.domain.repository.ImageUploadRepository;
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

@Service
@Transactional
@RequiredArgsConstructor
public class ProductImageCommandService implements ProductImageCommandUseCase {
  private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

  private final ProductRepository productRepository;
  private final ImageRepository imageRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ImageStoragePort imageStoragePort;
  private final ImageUploadRepository imageUploadRepository;

  public void addImages(AddImagesCommand command) {
    UUID productId = command.productId();
    List<UUID> imageUploadIds = command.imageUploadIds();

    if (CollectionUtils.isEmpty(imageUploadIds)) return;

    for (int sortOrder = 0; sortOrder < imageUploadIds.size(); sortOrder++) {
      UUID uploadId = imageUploadIds.get(sortOrder);

      ImageUpload imageUpload =
          imageUploadRepository
              .findById(uploadId)
              .orElseThrow(() -> new BusinessException(ProductErrorCode.IMAGE_UPLOAD_NOT_FOUND));

      validateUploadOwner(imageUpload, command.creatorId());
      validateFileSize(imageStoragePort.getContentLength(imageUpload.getObjectKey()));

      Image image =
          imageRepository.save(
              Image.create(productId, imageUpload.getObjectKey(), null, sortOrder));
      imageUploadRepository.delete(imageUpload);

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
    getProductForUpdate(command.productId()).validateOwner(command.creatorId());

    List<Image> images = imageRepository.findAllByProductIdAndDeletedAtIsNull(command.productId());

    Map<UUID, Image> imageMap =
        images.stream().collect(Collectors.toMap(Image::getId, Function.identity()));

    Set<UUID> requestedImageIds = new HashSet<>(command.imageIds());

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
    getProductForUpdate(command.productId()).validateOwner(command.creatorId());

    boolean deleted =
        imageRepository.softDelete(command.imageId(), command.productId(), command.creatorId());

    if (!deleted) throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_NOT_FOUND);
  }

  private Product getProductForUpdate(UUID productId) {
    return productRepository
        .findByIdForUpdate(productId)
        .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
  }

  private void validateUploadOwner(ImageUpload imageUpload, UUID creatorId) {
    if (!imageUpload.getCreatedBy().equals(creatorId)) {
      throw new BusinessException(ProductErrorCode.IMAGE_UPLOAD_NOT_FOUND);
    }
  }

  private void validateFileSize(long contentLength) {
    if (contentLength > MAX_FILE_SIZE) throw new BusinessException(CommonErrorCode.CONTENT_TOO_LARGE);
  }
}
