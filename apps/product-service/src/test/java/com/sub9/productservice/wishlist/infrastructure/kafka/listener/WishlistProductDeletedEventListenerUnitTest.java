package com.sub9.productservice.wishlist.infrastructure.kafka.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sub9.common.kafka.event.ProductDeletedEvent;
import com.sub9.productservice.wishlist.application.port.in.CleanupWishlistUseCase;
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
@DisplayName("WishlistProductDeletedEventListener - 단위 테스트")
class WishlistProductDeletedEventListenerUnitTest {
  @Mock private CleanupWishlistUseCase cleanupWishlistUseCase;
  @Mock private Acknowledgment acknowledgment;
  @InjectMocks private WishlistProductDeletedEventListener wishlistProductDeletedEventListener;

  private final UUID productId = UUID.randomUUID();

  @Test
  @DisplayName("상품 삭제 이벤트로 관심상품 정리에 성공한 뒤 ACK를 호출한다.")
  void handleProductDeleted_success() {
    // given
    ProductDeletedEvent event =
        new ProductDeletedEvent(UUID.randomUUID(), productId, Instant.now());

    // when
    wishlistProductDeletedEventListener.handleProductDeleted(event, acknowledgment);

    // then
    InOrder inOrder = inOrder(cleanupWishlistUseCase, acknowledgment);
    inOrder.verify(cleanupWishlistUseCase).cleanUpWByProductId(productId);
    inOrder.verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("관심상품 정리에 실패하면 예외를 전파하고 ACK를 호출하지 않는다.")
  void handleProductDeleted_fails_when_cleanup_fails() {
    // given
    ProductDeletedEvent event =
        new ProductDeletedEvent(UUID.randomUUID(), productId, Instant.now());
    willThrow(new IllegalStateException("삭제 실패"))
        .given(cleanupWishlistUseCase)
        .cleanUpWByProductId(productId);

    // when & then
    assertThatThrownBy(
            () -> wishlistProductDeletedEventListener.handleProductDeleted(event, acknowledgment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("관심 상품 정리 작업 실패");
    verifyNoInteractions(acknowledgment);
  }
}
