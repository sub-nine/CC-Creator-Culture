package com.sub9.productservice.product.application.command.service.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.sub9.common.kafka.event.ProductViewSyncEvent;
import com.sub9.productservice.product.application.port.out.product.ProductViewCountPublisher;
import com.sub9.productservice.product.application.port.out.product.ProductViewRepository;
import com.sub9.productservice.product.application.query.dto.ProductViewCount;
import com.sub9.productservice.product.infrastructure.scheduler.ProductTotalViewCountScheduler;
import com.sub9.productservice.product.infrastructure.scheduler.ProductViewCountScheduler;
import com.sub9.productservice.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("ProductViewCountPublishService - 통합 테스트")
class ProductViewCountPublishServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired private ProductViewCountPublishService productViewCountPublishService;

  @MockitoBean private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  @MockitoBean private ProductViewRepository productViewRepository;
  @MockitoBean private ProductViewCountPublisher productViewCountPublisher;
  @MockitoBean private ProductViewCountScheduler productViewCountScheduler;
  @MockitoBean private ProductTotalViewCountScheduler productTotalViewCountScheduler;

  @Nested
  @DisplayName("조회수 발행 테스트")
  class SyncViewCountsTests {
    @Test
    @DisplayName("조회수 이벤트를 발행하고 성공 응답 이후에만 Redis 집계를 삭제한다.")
    void syncViewCounts_success() {
      // given
      UUID productId = UUID.randomUUID();

      given(productViewRepository.findAllViewCounts())
          .willReturn(List.of(new ProductViewCount(productId, 3L)));

      CompletableFuture<Void> publishResult = new CompletableFuture<>();

      given(productViewCountPublisher.publish(any())).willReturn(publishResult);

      LocalDate before = LocalDate.now(Clock.systemUTC());

      // when
      int count = productViewCountPublishService.syncViewCounts();

      // then
      ArgumentCaptor<ProductViewSyncEvent> captor =
          ArgumentCaptor.forClass(ProductViewSyncEvent.class);

      verify(productViewCountPublisher).publish(captor.capture());

      assertThat(count).isEqualTo(1);
      assertThat(captor.getValue().eventId()).isNotNull();
      assertThat(captor.getValue().viewDate()).isBetween(before, LocalDate.now(Clock.systemUTC()));
      assertThat(captor.getValue().productViewCounts())
          .containsExactly(new ProductViewSyncEvent.ProductViewCount(productId, 3L));

      verify(productViewRepository, never()).deleteAllViewCount();

      publishResult.complete(null);

      verify(productViewRepository).deleteAllViewCount();
    }

    @Test
    @DisplayName("집계가 비어 있으면 이벤트를 발행하지 않는다.")
    void syncViewCounts_success_when_empty() {
      // given
      given(productViewRepository.findAllViewCounts()).willReturn(List.of());

      // when
      int count = productViewCountPublishService.syncViewCounts();

      // then
      assertThat(count).isZero();
      verifyNoInteractions(productViewCountPublisher);
      verify(productViewRepository, never()).deleteAllViewCount();
    }
  }
}
