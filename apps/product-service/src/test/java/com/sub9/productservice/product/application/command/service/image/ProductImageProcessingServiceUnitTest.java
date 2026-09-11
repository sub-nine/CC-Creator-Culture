package com.sub9.productservice.product.application.command.service.image;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.product.application.command.dto.product.*;
import com.sub9.productservice.product.application.port.out.image.*;
import com.sub9.productservice.product.application.support.ImageStorageRollbackCleaner;
import com.sub9.productservice.product.domain.model.*;
import com.sub9.productservice.product.domain.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImageProcessingService - 단위 테스트")
class ProductImageProcessingServiceUnitTest {
  @Mock ImageCommandRepository imageCommandRepository;
  @Mock ImageStoragePort imageStoragePort;
  @Mock ImageProcessorPort imageProcessorPort;
  @Mock ImageStorageRollbackCleaner imageStorageRollbackCleaner;
  @InjectMocks ProductImageProcessingService imageService;

  @Test
  @DisplayName("삭제되었거나 이미 처리된 이미지는 처리 완료 변경에 실패한다.")
  void resizeImage_fails_when_image_cannot_be_completed() {
    // given
    UUID imageId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    ImageData original = new ImageData("image/png", new byte[] {1});
    ImageData processed = new ImageData("image/jpeg", new byte[] {2});

    given(imageStoragePort.download("original/image")).willReturn(original);
    given(imageProcessorPort.resize(original)).willReturn(processed);
    given(imageCommandRepository.completeProcessing(eq(imageId), anyString())).willReturn(false);

    // when & then
    assertThatThrownBy(() -> imageService.resizeImage(imageId, productId, "original/image"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(CommonErrorCode.INTERNAL_SERVER_ERROR.message());
  }
}
