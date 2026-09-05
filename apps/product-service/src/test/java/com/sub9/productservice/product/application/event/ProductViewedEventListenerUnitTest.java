package com.sub9.productservice.product.application.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.sub9.productservice.product.domain.repository.ProductViewRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@DisplayName("ProductViewedEventListener - 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ProductViewedEventListenerUnitTest {
  @Mock private ProductViewRepository productViewRepository;
  @InjectMocks private ProductViewedEventListener productViewedEventListener;

  @Test
  @DisplayName("Redis 연결 실패가 상품 조회 호출자에게 전파되지 않는다.")
  void handleProductViewedEvent_fails_when_redis_unavailable() {
    // given
    ProductViewedEvent event = new ProductViewedEvent(UUID.randomUUID(), UUID.randomUUID());
    Duration ttl = Duration.ofMinutes(30);

    willThrow(new DataAccessResourceFailureException("Redis 연결 실패"))
        .given(productViewRepository)
        .recordView(event.productId(), event.viewerId(), ttl);

    // when & then
    assertThatCode(() -> productViewedEventListener.handleProductViewedEvent(event))
        .doesNotThrowAnyException();

    verify(productViewRepository).recordView(event.productId(), event.viewerId(), ttl);
  }
}
