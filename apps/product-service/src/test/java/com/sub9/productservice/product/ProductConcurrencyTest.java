package com.sub9.productservice.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.ErrorCode;
import com.sub9.productservice.product.application.command.dto.product.IncrementDailyViewCountsCommand;
import com.sub9.productservice.product.application.command.dto.sku.AddSkuCommand;
import com.sub9.productservice.product.application.command.dto.sku.UpdateSkuCommand;
import com.sub9.productservice.product.application.command.dto.stock.DeductStockCommand;
import com.sub9.productservice.product.application.command.dto.stock.RestoreStockCommand;
import com.sub9.productservice.product.application.port.in.sku.SkuCommandUseCase;
import com.sub9.productservice.product.application.port.in.stock.OrderStockUseCase;
import com.sub9.productservice.product.application.port.in.view.IncrementDailyViewCountsUseCase;
import com.sub9.productservice.product.domain.exception.StockErrorCode;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.domain.model.StockHistoryReason;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.sku.SkuCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.stock.StockCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.stock.StockHistoryCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.scheduler.ProductTotalViewCountScheduler;
import com.sub9.productservice.review.application.command.dto.CreateReviewCommand;
import com.sub9.productservice.review.application.port.in.ReviewCommandUseCase;
import com.sub9.productservice.review.application.port.out.ReviewOrderQueryPort;
import com.sub9.productservice.review.application.port.out.dto.ProductPurchaseInfo;
import com.sub9.productservice.review.domain.exception.ReviewErrorCode;
import com.sub9.productservice.review.infrastructure.persistence.command.ReviewJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import com.sub9.productservice.support.ConcurrencyTestingUtil;
import java.time.LocalDate;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("Product - 동시성 테스트")
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
public class ProductConcurrencyTest extends AbstractIntegrationTest {
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired SkuCommandJpaRepository skuRepository;
  @Autowired SkuCommandUseCase skuCommandUseCase;
  @Autowired StockHistoryCommandJpaRepository stockHistoryRepository;
  @Autowired StockCommandJpaRepository stockRepository;
  @Autowired OrderStockUseCase orderStockUseCase;
  @MockitoBean ReviewOrderQueryPort reviewOrderQueryPort;
  @Autowired ReviewCommandUseCase reviewCommandUseCase;
  @Autowired ReviewJpaRepository reviewRepository;
  @Autowired IncrementDailyViewCountsUseCase dailyViewCountsUseCase;
  @Autowired ProductTotalViewCountScheduler totalViewCountScheduler;

  private UUID creatorId;
  private Product product;
  private Stock stock;

  @BeforeEach
  void setUp() {
    creatorId = UUID.randomUUID();
    product = productRepository.save(Product.create(creatorId, "말랑이", "설명"));
    Sku sku = skuRepository.save(Sku.create(product.getId(), "핑크 | M", 10000L, true));
    stock = stockRepository.save(Stock.create(sku.getId(), 10));
  }

  @AfterEach
  void tearDown() {
    reviewRepository.deleteAllInBatch();
    stockHistoryRepository.deleteAllInBatch();
    stockRepository.deleteAllInBatch();
    skuRepository.deleteAllInBatch();
    productRepository.deleteAllInBatch();
  }

  @Nested
  @DisplayName("SkuCommandService - 동시성 테스트")
  class SkuConcurrencyTests {
    @Test
    @DisplayName("대표 SKU를 동시에 등록해도 대표 SKU는 하나만 유지한다.")
    void addSku_success_when_default_skus_are_added_concurrently() throws Exception {
      // given
      int threadCount = 3;

      // when
      ConcurrencyTestingUtil.run(
          threadCount,
          () ->
              skuCommandUseCase.addSku(
                  new AddSkuCommand(product.getId(), creatorId, "옵션", 10000L, true, 10)));

      // then
      var skus = skuRepository.findAll();
      assertThat(skus).filteredOn(Sku::isDefault).hasSize(1);
      assertThat(skus).hasSize(4);
    }

    @Test
    @DisplayName("대표가 아닌 서로 다른 SKU를 동시에 대표로 변경해도 최종적으로 대표 SKU는 하나여야 한다.")
    void updateSku_success_when_default_skus_are_changed_concurrently() throws Exception {
      // given
      AtomicInteger index = new AtomicInteger();

      List<Sku> updateSkus =
          List.of(
              Sku.create(product.getId(), "1번", 10000L, false),
              Sku.create(product.getId(), "2번", 10000L, false));

      skuRepository.saveAll(updateSkus);

      // when
      ConcurrencyTestingUtil.run(
          updateSkus.size(),
          () -> {
            Sku sku = updateSkus.get(index.getAndIncrement());

            skuCommandUseCase.updateSku(
                new UpdateSkuCommand(
                    creatorId, product.getId(), sku.getId(), sku.getName(), sku.getPrice(), true));
          });

      // then
      var skus = skuRepository.findAllByProductIdAndDeletedAtIsNull(product.getId());
      assertThat(skus).filteredOn(Sku::isDefault).hasSize(1);
    }
  }

  @Nested
  @DisplayName("StockCommandService - 동시성 테스트")
  class StockConcurrencyTests {
    @Test
    @DisplayName("동시에 재고를 차감해도 재고 수량의 정합성을 유지한다.")
    void deduct_success_when_requested_concurrently() throws Exception {
      // given
      int threadCount = 3;

      // when
      ConcurrencyTestingUtil.run(
          threadCount,
          () ->
              orderStockUseCase.deduct(
                  new DeductStockCommand(
                      UUID.randomUUID(),
                      List.of(new DeductStockCommand.Item(stock.getSkuId(), 3)))));

      // then
      assertThat(stockRepository.findById(stock.getId()).orElseThrow().getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("동시에 차감 요청이 들어왔을 때 재고를 초과한 요청은 실패한다.")
    void deduct_fails_when_concurrent_requests_exceed_stock() throws Exception {
      // given
      Queue<ErrorCode> errorCodes = new ConcurrentLinkedQueue<>();
      int threadCount = 5;

      // when
      ConcurrencyTestingUtil.run(
          threadCount,
          () -> {
            try {
              orderStockUseCase.deduct(
                  new DeductStockCommand(
                      UUID.randomUUID(),
                      List.of(new DeductStockCommand.Item(stock.getSkuId(), 3))));
            } catch (BusinessException e) {
              errorCodes.add(e.getErrorCode());
            }
          });

      // then
      assertThat(stockRepository.findById(stock.getId()).orElseThrow().getQuantity()).isEqualTo(1);
      assertThat(errorCodes).hasSize(2).containsOnly(StockErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("동일 주문의 재고 차감을 동시에 요청해도 한 번만 반영한다.")
    void deduct_success_when_same_order_is_requested_concurrently() throws Exception {
      // given
      UUID orderId = UUID.randomUUID();
      int threadCount = 5;

      // when
      ConcurrencyTestingUtil.run(
          threadCount,
          () ->
              orderStockUseCase.deduct(
                  new DeductStockCommand(
                      orderId, List.of(new DeductStockCommand.Item(stock.getSkuId(), 3)))));

      // then
      assertThat(stockRepository.findById(stock.getId()).orElseThrow().getQuantity()).isEqualTo(7);
      assertThat(stockHistoryRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 주문의 재고 복구를 동시에 요청해도 한 번만 반영한다.")
    void restore_success_when_same_order_is_requested_concurrently() throws Exception {
      // given
      UUID orderId = UUID.randomUUID();
      int threadCount = 5;

      orderStockUseCase.deduct(
          new DeductStockCommand(
              orderId, List.of(new DeductStockCommand.Item(stock.getSkuId(), 3))));

      // when
      ConcurrencyTestingUtil.run(
          threadCount,
          () ->
              orderStockUseCase.restore(
                  new RestoreStockCommand(
                      orderId,
                      List.of(new RestoreStockCommand.Item(stock.getSkuId(), 3)),
                      StockHistoryReason.ORDER_CANCEL)));

      // then
      assertThat(stockRepository.findById(stock.getId()).orElseThrow().getQuantity()).isEqualTo(10);
      assertThat(stockHistoryRepository.count()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("ReviewCommandService - 동시성 테스트")
  class ReviewConcurrencyTests {
    @Test
    @DisplayName("같은 주문건에 대해 리뷰를 동시에 등록하면 하나만 성공해야한다.")
    void createReview() throws Exception {
      // given
      Queue<ErrorCode> errorCodes = new ConcurrentLinkedQueue<>();
      UUID orderItemId = UUID.randomUUID();
      int threadCount = 3;

      CreateReviewCommand command =
          new CreateReviewCommand(UUID.randomUUID(), orderItemId, 5, "리뷰");

      ProductPurchaseInfo purchaseInfo = new ProductPurchaseInfo(UUID.randomUUID(), true);
      given(reviewOrderQueryPort.getPurchaseInfo(any(), any())).willReturn(purchaseInfo);

      // when
      ConcurrencyTestingUtil.run(
          threadCount,
          () -> {
            try {
              reviewCommandUseCase.createReview(command);
            } catch (BusinessException e) {
              errorCodes.add(e.getErrorCode());
            }
          });

      // then
      assertThat(reviewRepository.findAll()).hasSize(1);
      assertThat(errorCodes).hasSize(2).containsOnly(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
    }
  }

  @Nested
  @DisplayName("ProductTotalViewCountScheduler - 동시성 테스트")
  class ViewCountConcurrencyTests {
    @Test
    @Disabled("중복 집계 방지 구현 후 활성화")
    @DisplayName("토탈 조회수 반영 스케쥴러가 동시에 실행되어도 전체 조회수에 한번만 반영한다.")
    void execute_concurrently() throws Exception {
      // given
      LocalDate yesterday = LocalDate.now().minusDays(1);

      dailyViewCountsUseCase.incrementDailyViewCounts(
          new IncrementDailyViewCountsCommand(
              UUID.randomUUID(),
              yesterday,
              List.of(new IncrementDailyViewCountsCommand.ViewCount(product.getId(), 1000L))));

      // when
      ConcurrencyTestingUtil.run(3, totalViewCountScheduler::execute);

      // then
      assertThat(productRepository.findById(product.getId()).orElseThrow().getViewCount())
          .isEqualTo(1000L);
    }
  }
}
