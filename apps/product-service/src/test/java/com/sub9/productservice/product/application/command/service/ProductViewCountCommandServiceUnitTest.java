package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.sub9.productservice.product.application.port.ProductViewCountPublisher;
import com.sub9.productservice.product.application.query.dto.ProductViewCount;
import com.sub9.productservice.product.domain.repository.ProductCommandRepository;
import com.sub9.productservice.product.domain.repository.ProductDailyViewCommandRepository;
import com.sub9.productservice.product.application.port.ProductViewRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@DisplayName("ProductViewCountCommandService - 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ProductViewCountCommandServiceUnitTest {
  @Mock private ProductDailyViewCommandRepository productDailyViewCommandRepository;
  @Mock private ProductViewCountPublisher productViewCountPublisher;
  @Mock private ProductCommandRepository productCommandRepository;
  @Mock private ProductViewRepository productViewRepository;
  @InjectMocks private ProductViewCountCommandService productViewCountCommandService;

  @Nested
  @DisplayName("조회수 발행 실패 테스트")
  class SyncViewCountsTests {
    @Test
    @DisplayName("Kafka 비동기 발행이 실패하면 Redis 조회수를 삭제하지 않는다.")
    void syncViewCounts_fails_when_publish_failed() {
      // given
      given(productViewRepository.findAllViewCounts())
          .willReturn(List.of(new ProductViewCount(UUID.randomUUID(), 3L)));

      CompletableFuture<Void> publishResult = new CompletableFuture<>();

      given(productViewCountPublisher.publish(any())).willReturn(publishResult);

      // when
      productViewCountCommandService.syncViewCounts();
      publishResult.completeExceptionally(new IllegalStateException("Kafka 발행 실패"));

      // then
      verify(productViewRepository, never()).deleteAllViewCount();
      verify(productViewRepository, never()).deleteAllViewCount();
    }

    @Test
    @DisplayName("Redis 조회가 실패하면 Kafka 발행과 조회수 삭제를 하지 않는다.")
    void syncViewCounts_fails_when_redis_unavailable() {
      // given
      given(productViewRepository.findAllViewCounts())
          .willThrow(new DataAccessResourceFailureException("Redis 연결 실패"));

      // when & then
      assertThatThrownBy(() -> productViewCountCommandService.syncViewCounts())
          .isInstanceOf(DataAccessResourceFailureException.class);

      verifyNoInteractions(productViewCountPublisher);
      verify(productViewRepository, never()).deleteAllViewCount();
    }
  }
}
