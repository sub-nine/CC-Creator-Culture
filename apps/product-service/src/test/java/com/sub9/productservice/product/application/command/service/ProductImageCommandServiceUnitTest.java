package com.sub9.productservice.product.application.command.service;

import static com.sub9.productservice.support.ImageTestFixture.imageBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.command.dto.product.UploadImageCommand;
import com.sub9.productservice.product.application.event.ProductImageUploadedEvent;
import com.sub9.productservice.product.application.port.ImageData;
import com.sub9.productservice.product.application.port.ImageProcessorPort;
import com.sub9.productservice.product.application.port.ImageStoragePort;
import com.sub9.productservice.product.domain.repository.ImageCommandRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImageCommandService - 단위 테스트")
class ProductImageCommandServiceUnitTest {
  @Mock ImageCommandRepository imageCommandRepository;
  @Mock ApplicationEventPublisher eventPublisher;
  @Mock ImageProcessorPort imageProcessorPort;
  @Mock ImageStoragePort imageStoragePort;
  @InjectMocks ProductImageCommandService imageService;

  @ParameterizedTest
  @NullAndEmptySource
  @DisplayName("이미지를 첨부하지 않으면 저장과 이벤트 발행을 생략한다.")
  void uploadImages_success_without_images(List<UploadImageCommand> images) {
    // when
    imageService.uploadImages(UUID.randomUUID(), images);

    // then
    verifyNoInteractions(imageCommandRepository, imageStoragePort, eventPublisher);
  }

  @Test
  @DisplayName("뒤쪽 이미지가 유효하지 않아도 앞쪽 이미지를 저장하지 않는다.")
  void uploadImages_fails_before_any_write_when_later_image_is_invalid() throws Exception {
    // given
    var images = List.of(
        new UploadImageCommand("image/png", imageBytes("png")),
        new UploadImageCommand("image/png", new byte[0]));

    // when & then
    assertThatThrownBy(() -> imageService.uploadImages(UUID.randomUUID(), images))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(imageCommandRepository, imageStoragePort, eventPublisher);
  }

  @Test
  @DisplayName("요청 Content-Type 대신 실제 이미지 형식으로 저장한다.")
  void uploadImages_success_with_detected_media_type() throws Exception {
    // given
    byte[] data = imageBytes("png");
    given(imageCommandRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    // when
    imageService.uploadImages(UUID.randomUUID(),
        List.of(new UploadImageCommand("application/octet-stream", data)));

    // then
    var captor = ArgumentCaptor.forClass(ImageData.class);
    verify(imageStoragePort).upload(anyString(), captor.capture());
    assertThat(captor.getValue().contentType()).isEqualTo("image/png");
    assertThat(captor.getValue().data()).containsExactly(data);
    verify(eventPublisher).publishEvent(any(ProductImageUploadedEvent.class));
  }
}
