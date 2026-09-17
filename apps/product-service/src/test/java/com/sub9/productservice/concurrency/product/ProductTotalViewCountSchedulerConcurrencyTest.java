package com.sub9.productservice.concurrency.product;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.sub9.productservice.product.application.command.dto.product.IncrementDailyViewCountsCommand;
import com.sub9.productservice.product.application.port.in.view.IncrementDailyViewCountsUseCase;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.scheduler.ProductTotalViewCountScheduler;
import com.sub9.productservice.support.AbstractIntegrationTest;
import com.sub9.productservice.support.ConcurrencyTestingUtil;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("ProductTotalViewCountScheduler - 동시성 테스트")
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
public class ProductTotalViewCountSchedulerConcurrencyTest extends AbstractIntegrationTest {
  @Autowired IncrementDailyViewCountsUseCase dailyViewCountsUseCase;
  @Autowired ProductTotalViewCountScheduler totalViewCountScheduler;
  @Autowired ProductCommandJpaRepository productJpaRepository;

  @Autowired EntityManager entityManager;

  private Product product;

  @BeforeEach
  void setUp() {
    product = productJpaRepository.save(Product.create(UUID.randomUUID(), "왁뿌볼", "설명"));

    LocalDate yesterday = LocalDate.now().minusDays(1);

    dailyViewCountsUseCase.incrementDailyViewCounts(
        new IncrementDailyViewCountsCommand(
            UUID.randomUUID(),
            yesterday,
            List.of(new IncrementDailyViewCountsCommand.ViewCount(product.getId(), 1000L))));
  }

  // TODO : 중복 집계 문제 있음. 스케쥴러 락 적용 필요
  @Test
  @Disabled("중복 집계 방지 구현 후 활성화")
  @DisplayName("토탈 조회수 반영 스케쥴러가 동시에 실행되어도 전체 조회수에 한번만 반영한다.")
  void execute_concurrently() throws Exception {
    // when
    ConcurrencyTestingUtil.run(3, totalViewCountScheduler::execute);

    // then
    assertThat(productJpaRepository.findById(product.getId()).orElseThrow().getViewCount())
        .isEqualTo(1000L);
  }
}
