package com.sub9.productservice.product.application.command.service.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sub9.productservice.product.application.command.dto.product.IncrementDailyViewCountsCommand;
import com.sub9.productservice.product.application.port.out.product.ProductViewCountPublisher;
import com.sub9.productservice.product.application.port.out.product.ProductViewRepository;
import com.sub9.productservice.product.domain.model.ProductDailyView;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductDailyViewCommandJPARepository;
import com.sub9.productservice.product.infrastructure.scheduler.ProductTotalViewCountScheduler;
import com.sub9.productservice.product.infrastructure.scheduler.ProductViewCountScheduler;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("ProductDailyViewCountService - 통합 테스트")
class ProductDailyViewCountServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired private ProductDailyViewCountService productDailyViewCountService;
  @Autowired private ProductDailyViewCommandJPARepository dailyViewRepository;
  @Autowired private EntityManager entityManager;

  @MockitoBean private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  @MockitoBean private ProductViewRepository productViewRepository;
  @MockitoBean private ProductViewCountPublisher productViewCountPublisher;
  @MockitoBean private ProductViewCountScheduler productViewCountScheduler;
  @MockitoBean private ProductTotalViewCountScheduler productTotalViewCountScheduler;

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

  private void increment(UUID productId, LocalDate viewDate, long count) {
    productDailyViewCountService.incrementDailyViewCounts(
        new IncrementDailyViewCountsCommand(
            UUID.randomUUID(),
            viewDate,
            List.of(new IncrementDailyViewCountsCommand.ViewCount(productId, count))));
  }
}
