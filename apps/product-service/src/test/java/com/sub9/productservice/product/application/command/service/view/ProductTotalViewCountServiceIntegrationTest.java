package com.sub9.productservice.product.application.command.service.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sub9.productservice.product.application.command.dto.product.IncrementDailyViewCountsCommand;
import com.sub9.productservice.product.application.port.out.product.ProductViewCountPublisher;
import com.sub9.productservice.product.application.port.out.product.ProductViewRepository;
import com.sub9.productservice.product.domain.model.Product;
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
@DisplayName("ProductTotalViewCountService - 통합 테스트")
class ProductTotalViewCountServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired private ProductTotalViewCountService productTotalViewCountService;
  @Autowired private ProductCommandJpaRepository productRepository;
  @Autowired private ProductDailyViewCommandJPARepository dailyViewRepository;
  @Autowired private EntityManager entityManager;

  @MockitoBean private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  @MockitoBean private ProductViewRepository productViewRepository;
  @MockitoBean private ProductViewCountPublisher productViewCountPublisher;
  @MockitoBean private ProductViewCountScheduler productViewCountScheduler;
  @MockitoBean private ProductTotalViewCountScheduler productTotalViewCountScheduler;

  @Autowired private ProductDailyViewCountService productDailyViewCountService;

  @Nested
  @DisplayName("상품 누적 조회수 반영 테스트")
  class SyncTotalViewCountsTests {
    @Test
    @DisplayName("전날 조회수를 DB에 직접 누적하고 삭제된 상품과 없는 상품은 제외한다.")
    void syncTotalViewCounts_success() {
      // given
      UUID creatorId = UUID.randomUUID();

      Product product = productRepository.save(Product.create(creatorId, "상품", "설명"));
      Product deleted = productRepository.save(Product.create(creatorId, "삭제 상품", "설명"));
      deleted.delete(creatorId);
      entityManager.flush();

      entityManager
          .createNativeQuery("UPDATE p_products SET view_count = 10 WHERE id = :productId")
          .setParameter("productId", product.getId())
          .executeUpdate();
      entityManager.clear();

      LocalDate yesterday = LocalDate.now(Clock.systemUTC()).minusDays(1);

      increment(product.getId(), yesterday, 5L);
      increment(product.getId(), yesterday.minusDays(1), 7L);
      increment(product.getId(), yesterday.plusDays(1), 9L);
      increment(deleted.getId(), yesterday, 4L);
      increment(UUID.randomUUID(), yesterday, 6L);

      entityManager.flush();
      entityManager.clear();

      // when
      productTotalViewCountService.syncTotalViewCounts();

      entityManager.flush();
      entityManager.clear();

      // then
      assertThat(productRepository.findById(product.getId()).orElseThrow().getViewCount())
          .isEqualTo(15L);
      assertThat(productRepository.findById(deleted.getId()).orElseThrow().getViewCount()).isZero();
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
