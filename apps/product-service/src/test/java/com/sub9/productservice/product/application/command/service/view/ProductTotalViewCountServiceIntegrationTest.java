package com.sub9.productservice.product.application.command.service.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.productservice.product.application.command.dto.product.IncrementDailyViewCountsCommand;
import com.sub9.productservice.product.application.port.out.product.ProductViewCountPublisher;
import com.sub9.productservice.product.application.port.out.product.ProductViewRepository;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductDailyView;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.scheduler.ProductTotalViewCountScheduler;
import com.sub9.productservice.product.infrastructure.scheduler.ProductViewCountScheduler;
import com.sub9.productservice.support.AbstractIntegrationTest;
import com.sub9.productservice.support.ConcurrencyTestingUtil;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@DisplayName("ProductTotalViewCountService - 통합 테스트")
class ProductTotalViewCountServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired private ProductTotalViewCountService productTotalViewCountService;
  @Autowired private ProductCommandJpaRepository productRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

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
    @Transactional
    @DisplayName("이전의 미집계 조회수를 누적하고 당일 조회수와 삭제되거나 없는 상품은 제외한다.")
    void syncTotalViewCounts_success() {
      // given
      UUID creatorId = UUID.randomUUID();

      Product product = productRepository.save(Product.create(creatorId, "상품", "설명"));
      Product deleted = productRepository.save(Product.create(creatorId, "삭제 상품", "설명"));
      UUID missingProductId = UUID.randomUUID();
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
      increment(missingProductId, yesterday, 6L);

      entityManager.flush();
      entityManager.clear();

      // when
      productTotalViewCountService.syncTotalViewCounts();

      entityManager.flush();
      entityManager.clear();

      // then
      assertThat(productRepository.findById(product.getId()).orElseThrow().getViewCount())
          .isEqualTo(22L);
      assertThat(productRepository.findById(deleted.getId()).orElseThrow().getViewCount()).isZero();
      assertThat(findDailyViews(yesterday))
          .filteredOn(
              view ->
                  view.getProductId().equals(product.getId())
                      || view.getProductId().equals(deleted.getId())
                      || view.getProductId().equals(missingProductId))
          .hasSize(3)
          .allMatch(view -> view.isAggregated());
      assertThat(findDailyViews(yesterday.minusDays(1)))
          .filteredOn(view -> view.getProductId().equals(product.getId()))
          .hasSize(1)
          .allMatch(view -> view.isAggregated());
      assertThat(findDailyViews(yesterday.plusDays(1)))
          .filteredOn(view -> view.getProductId().equals(product.getId()))
          .hasSize(1)
          .allMatch(view -> !view.isAggregated());
    }
  }

  @Test
  @DisplayName("동시 집계와 재실행 시 전날 조회수를 한 번만 누적하고 집계 완료 처리한다.")
  void syncTotalViewCounts_success_when_concurrent_and_retried() throws Exception {
    // given
    Product product = productRepository.save(Product.create(UUID.randomUUID(), "상품", "설명"));
    LocalDate yesterday = LocalDate.now(Clock.systemUTC()).minusDays(1);

    try {
      increment(product.getId(), yesterday, 1000L);

      // when
      ConcurrencyTestingUtil.run(3, productTotalViewCountService::syncTotalViewCounts);
      productTotalViewCountService.syncTotalViewCounts();

      // then
      assertThat(productRepository.findById(product.getId()).orElseThrow().getViewCount())
          .isEqualTo(1000L);
      assertThat(findDailyViews(yesterday))
          .filteredOn(view -> view.getProductId().equals(product.getId()))
          .singleElement()
          .satisfies(view -> assertThat(view.isAggregated()).isTrue());
    } finally {
      deleteProductViews(product.getId());
    }
  }

  private List<ProductDailyView> findDailyViews(LocalDate viewDate) {
    return entityManager
        .createQuery(
            "SELECT v FROM ProductDailyView v WHERE v.viewDate = :viewDate", ProductDailyView.class)
        .setParameter("viewDate", viewDate)
        .getResultList();
  }

  private void deleteProductViews(UUID productId) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              entityManager
                  .createQuery("DELETE FROM ProductDailyView v WHERE v.productId = :id")
                  .setParameter("id", productId)
                  .executeUpdate();
              entityManager
                  .createNativeQuery("DELETE FROM p_products WHERE id = :id")
                  .setParameter("id", productId)
                  .executeUpdate();
            });
  }

  private void increment(UUID productId, LocalDate viewDate, long count) {
    productDailyViewCountService.incrementDailyViewCounts(
        new IncrementDailyViewCountsCommand(
            UUID.randomUUID(),
            viewDate,
            List.of(new IncrementDailyViewCountsCommand.ViewCount(productId, count))));
  }
}
