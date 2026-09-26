package com.sub9.productservice.product.application.command.service.image;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.product.application.command.dto.product.CreatePresignedUrlCommand;
import com.sub9.productservice.product.application.command.dto.product.CreatePresignedUrlResult;
import com.sub9.productservice.product.application.port.in.image.ImageUploadUseCase;
import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import com.sub9.productservice.product.domain.model.ImageUpload;
import com.sub9.productservice.product.domain.repository.ImageUploadRepository;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageUploadService implements ImageUploadUseCase {
  private static final Map<String, String> CONTENT_TYPE_EXTENSIONS =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png");
  private static final String ORIGINAL_KEY_FORMAT = "products/images/original/%s.%s";
  private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MiB
  private static final Duration EXPIRATION = Duration.ofMinutes(10);

  private final ImageUploadRepository imageRepository;
  private final ImageStoragePort imageStoragePort;

  @Override
  public CreatePresignedUrlResult createPresignedUrl(CreatePresignedUrlCommand command) {
    String extension = validateAndGetExtension(command);

    String objectKey = ORIGINAL_KEY_FORMAT.formatted(UUID.randomUUID(), extension);

    String uploadUrl =
        imageStoragePort.createPresignedPutUrl(objectKey, command.contentType(), EXPIRATION);

    var savedImageUpload =
        imageRepository.save(ImageUpload.create(objectKey, command.contentType()));

    return new CreatePresignedUrlResult(savedImageUpload.getId(), uploadUrl);
  }

  private String validateAndGetExtension(CreatePresignedUrlCommand command) {
    if (command.fileSize() > MAX_FILE_SIZE) {
      throw new BusinessException(CommonErrorCode.CONTENT_TOO_LARGE);
    }

    String extension = CONTENT_TYPE_EXTENSIONS.get(command.contentType());

    if (extension == null) {
      throw new BusinessException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    return extension;
  }
}
