package com.sub9.productservice.product.application.command.service.image;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.product.application.command.dto.product.CreatePresignedUrlCommand;
import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import com.sub9.productservice.product.domain.repository.ImageUploadRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageUploadService - 단위 테스트")
class ImageUploadServiceUnitTest {
  @Mock ImageUploadRepository imageUploadRepository;
  @Mock ImageStoragePort imageStoragePort;
  @InjectMocks ImageUploadService imageUploadService;

  @Test
  @DisplayName("허용된 파일 크기를 초과하면 presigned URL 발급을 거절한다.")
  void createPresignedUrl_fails_when_file_size_exceeds_limit() {
    var command =
        new CreatePresignedUrlCommand(UUID.randomUUID(), "image/png", 5 * 1024 * 1024L + 1);

    assertThatThrownBy(() -> imageUploadService.createPresignedUrl(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(CommonErrorCode.CONTENT_TOO_LARGE.message());
    then(imageStoragePort).shouldHaveNoInteractions();
    then(imageUploadRepository).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("허용하지 않은 이미지 타입이면 presigned URL 발급을 거절한다.")
  void createPresignedUrl_fails_when_content_type_is_unsupported() {
    var command = new CreatePresignedUrlCommand(UUID.randomUUID(), "image/gif", 1024L);

    assertThatThrownBy(() -> imageUploadService.createPresignedUrl(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.message());
    then(imageStoragePort).shouldHaveNoInteractions();
    then(imageUploadRepository).shouldHaveNoInteractions();
  }
}
