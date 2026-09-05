package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.sub9.common.kafka.event.ProductViewSyncEvent;
import com.sub9.productservice.product.application.command.dto.IncrementDailyViewCountsCommand;
import com.sub9.productservice.product.application.port.ProductViewCountPublisher;
import com.sub9.productservice.product.application.query.dto.ProductViewCount;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductDailyView;
import com.sub9.productservice.product.application.port.ProductViewRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductDailyViewCommandJPARepository;
import com.sub9.productservice.product.infrastructure.scheduler.ProductTotalViewCountScheduler;
import com.sub9.productservice.product.infrastructure.scheduler.ProductViewCountScheduler;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
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
@DisplayName("ProductViewCountCommandService - 통합 테스트")
class ProductViewCountCommandServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired private ProductViewCountCommandService productViewCountCommandService;
  @Autowired private ProductCommandJpaRepository productRepository;
  @Autowired private ProductDailyViewCommandJPARepository dailyViewRepository;
  @Autowired private EntityManager entityManager;

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
      int count = productViewCountCommandService.syncViewCounts();

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
      int count = productViewCountCommandService.syncViewCounts();

      // then
      assertThat(count).isZero();
      verifyNoInteractions(productViewCountPublisher);
      verify(productViewRepository, never()).deleteAllViewCount();
    }
  }

  @Nested
  @DisplayName("일별 조회수 저장 테스트")
  class IncrementDailyViewCountsTests {
    @Test
    @DisplayName("같은 상품과 날짜는 한 행에 누적하고 다른 날짜는 별도로 저장한다.")
    void incrementDailyViewCounts_success() {
      // given
      UUID productId = UUID.randomUUID();
      LocalDate viewDate = LocalDate.of(2026, 9, 1);

      // when
      increment(productId, viewDate, 3L);
      increment(productId, viewDate, 4L);
      increment(productId, viewDate.plusDays(1), 2L);
      entityManager.flush();
      entityManager.clear();

      // then
      List<ProductDailyView> views = dailyViewRepository.findAllByViewDate(viewDate);
      assertThat(views).hasSize(1);
      assertThat(views.getFirst().getProductId()).isEqualTo(productId);
      assertThat(views.getFirst().getViewCount()).isEqualTo(7L);
      assertThat(dailyViewRepository.findAllByViewDate(viewDate.plusDays(1)))
          .extracting(ProductDailyView::getViewCount)
          .containsExactly(2L);
    }
  }

  @Nested
  @DisplayName("상품 누적 조회수 반영 테스트")
  class SyncTotalViewCountsTests {
    @Test
    @DisplayName("전날 조회수만 누적하고 삭제된 상품과 없는 상품은 건너뛴다.")
    void syncTotalViewCounts_success() {
      // given
      UUID creatorId = UUID.randomUUID();

      Product product = productRepository.save(Product.create(creatorId, "상품", "설명"));
      product.incrementViewCount(10L);
      Product deleted = productRepository.save(Product.create(creatorId, "삭제 상품", "설명"));
      deleted.delete(creatorId);
      LocalDate yesterday = LocalDate.now(Clock.systemUTC()).minusDays(1);

      increment(product.getId(), yesterday, 5L);
      increment(product.getId(), yesterday.minusDays(1), 7L);
      increment(product.getId(), yesterday.plusDays(1), 9L);
      increment(deleted.getId(), yesterday, 4L);
      increment(UUID.randomUUID(), yesterday, 6L);

      entityManager.flush();
      entityManager.clear();

      // when
      productViewCountCommandService.syncTotalViewCounts();

      entityManager.flush();
      entityManager.clear();

      // then
      assertThat(productRepository.findById(product.getId()).orElseThrow().getViewCount())
          .isEqualTo(15L);
      assertThat(productRepository.findById(deleted.getId()).orElseThrow().getViewCount()).isZero();
    }
  }

  private void increment(UUID productId, LocalDate viewDate, long count) {
    productViewCountCommandService.incrementDailyViewCounts(
        new IncrementDailyViewCountsCommand(
            UUID.randomUUID(),
            viewDate,
            List.of(new IncrementDailyViewCountsCommand.ViewCount(productId, count))));
  }
}
