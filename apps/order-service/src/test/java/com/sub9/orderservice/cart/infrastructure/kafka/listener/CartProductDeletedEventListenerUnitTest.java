package com.sub9.orderservice.cart.infrastructure.kafka.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sub9.common.kafka.event.ProductDeletedEvent;
import com.sub9.orderservice.cart.application.port.in.CartCleanupUseCase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartProductDeletedEventListener - 단위 테스트")
class CartProductDeletedEventListenerUnitTest {
  @Mock private CartCleanupUseCase cartCleanupUseCase;
  @Mock private Acknowledgment acknowledgment;
  @InjectMocks private CartProductDeletedEventListener cartProductDeletedEventListener;

  private final UUID productId = UUID.randomUUID();

  @Test
  @DisplayName("상품 삭제 이벤트 처리에 성공하면 ACK 처리한다.")
  void handleProductDeleted_success() {
    // given
    ProductDeletedEvent event =
        new ProductDeletedEvent(UUID.randomUUID(), productId, Instant.now());

    // when
    cartProductDeletedEventListener.handleProductDeleted(event, acknowledgment);

    // then
    InOrder inOrder = inOrder(cartCleanupUseCase, acknowledgment);
    inOrder.verify(cartCleanupUseCase).cleanupByProductId(productId);
    inOrder.verify(acknowledgment).acknowledge();
    verifyNoMoreInteractions(cartCleanupUseCase, acknowledgment);
  }

  @Test
  @DisplayName("장바구니 정리에 실패하면 예외를 전파하고 ACK 처리하지 않는다.")
  void handleProductDeleted_fails_when_cleanup_fails() {
    // given
    ProductDeletedEvent event =
        new ProductDeletedEvent(UUID.randomUUID(), productId, Instant.now());
    IllegalStateException failure = new IllegalStateException("삭제 실패");
    willThrow(failure).given(cartCleanupUseCase).cleanupByProductId(productId);

    // when & then
    assertThatThrownBy(
            () -> cartProductDeletedEventListener.handleProductDeleted(event, acknowledgment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("장바구니 정리 작업 실패")
        .hasCause(failure);
    verifyNoInteractions(acknowledgment);
  }
}
