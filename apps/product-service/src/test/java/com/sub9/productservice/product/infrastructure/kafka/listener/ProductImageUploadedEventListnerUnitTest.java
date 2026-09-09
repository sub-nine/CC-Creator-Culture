package com.sub9.productservice.product.infrastructure.kafka.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.product.application.command.service.ProductImageCommandService;
import com.sub9.productservice.product.application.event.ProductImageUploadedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImageUploadedEventListner - 단위 테스트")
class ProductImageUploadedEventListnerUnitTest {
  @Mock ProductImageCommandService imageService;
  @Mock Acknowledgment ack;
  @InjectMocks ProductImageUploadedEventListner listener;

  private final ProductImageUploadedEvent event =
      new ProductImageUploadedEvent(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "original/image");

  @Test
  @DisplayName("이미지 처리 성공 이후에만 ACK한다.")
  void handleProducImageUploadEvent_success() {
    // when
    listener.handleProducImageUploadEvent(event, ack);

    // then
    var order = inOrder(imageService, ack);

    order.verify(imageService).resizeImage(event.imageId(), event.productId(), event.originalKey());
    order.verify(ack).acknowledge();
  }

  @Test
  @DisplayName("이미지 처리 실패 시 ACK하지 않고 재시도할 수 있도록 예외를 전파한다.")
  void handleProducImageUploadEvent_fails_when_processing_fails() {
    // given
    willThrow(new BusinessException(CommonErrorCode.BAD_REQUEST))
        .given(imageService)
        .resizeImage(event.imageId(), event.productId(), event.originalKey());

    // when & then
    assertThatThrownBy(() -> listener.handleProducImageUploadEvent(event, ack))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("이미지 리사이징 작업 실패");
    verifyNoInteractions(ack);
  }
}
