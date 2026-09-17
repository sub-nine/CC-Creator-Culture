package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.ErrorCode;
import com.sub9.productservice.product.application.command.dto.stock.DeductStockCommand;
import com.sub9.productservice.product.application.command.dto.stock.RestoreStockCommand;
import com.sub9.productservice.product.domain.exception.StockErrorCode;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.domain.model.StockHistoryReason;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.sku.SkuCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.stock.StockCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.stock.StockHistoryCommandJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import com.sub9.productservice.support.ConcurrencyTestingUtil;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("StockCommandService - 동시성 테스트")
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class StockConcurrencyTest extends AbstractIntegrationTest {
  @Autowired StockCommandService stockCommandService;
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired SkuCommandJpaRepository skuRepository;
  @Autowired StockCommandJpaRepository stockRepository;
  @Autowired StockHistoryCommandJpaRepository stockHistoryRepository;

  private Stock stock;

  @BeforeEach
  void setUp() {
    Product product = productRepository.save(Product.create(UUID.randomUUID(), "말랑이", "설명"));
    Sku sku = skuRepository.save(Sku.create(product.getId(), "핑크 | M", 10000L, true));
    stock = stockRepository.save(Stock.create(sku.getId(), 10));
  }

  @AfterEach
  void tearDown() {
    stockHistoryRepository.deleteAllInBatch();
    stockRepository.deleteAllInBatch();
    skuRepository.deleteAllInBatch();
    productRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("동시에 재고를 차감해도 재고 수량의 정합성을 유지한다.")
  void deduct_success_when_requested_concurrently() throws Exception {
    // given
    int threadCount = 3;

    // when
    ConcurrencyTestingUtil.run(
        threadCount,
        () ->
            stockCommandService.deduct(
                new DeductStockCommand(
                    UUID.randomUUID(), List.of(new DeductStockCommand.Item(stock.getSkuId(), 3)))));

    // then
    assertThat(stockRepository.findById(stock.getId()).orElseThrow().getQuantity()).isEqualTo(1);
  }

  @Test
  @DisplayName("동시에 차감 요청이 들어왔을 때 재고를 초과한 요청은 실패한다.")
  void deduct_fails_when_concurrent_requests_exceed_stock() throws Exception {
    // given
    Queue<ErrorCode> errors = new ConcurrentLinkedQueue<>();
    int threadCount = 5;

    // when
    ConcurrencyTestingUtil.run(
        threadCount,
        () -> {
          try {
            stockCommandService.deduct(
                new DeductStockCommand(
                    UUID.randomUUID(), List.of(new DeductStockCommand.Item(stock.getSkuId(), 3))));
          } catch (BusinessException e) {
            errors.add(e.getErrorCode());
          }
        });

    // then
    assertThat(stockRepository.findById(stock.getId()).orElseThrow().getQuantity()).isEqualTo(1);
    assertThat(errors).hasSize(2).containsOnly(StockErrorCode.INSUFFICIENT_STOCK);
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
            stockCommandService.deduct(
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

    stockCommandService.deduct(
        new DeductStockCommand(orderId, List.of(new DeductStockCommand.Item(stock.getSkuId(), 3))));

    // when
    ConcurrencyTestingUtil.run(
        threadCount,
        () ->
            stockCommandService.restore(
                new RestoreStockCommand(
                    orderId,
                    List.of(new RestoreStockCommand.Item(stock.getSkuId(), 3)),
                    StockHistoryReason.ORDER_CANCEL)));

    // then
    assertThat(stockRepository.findById(stock.getId()).orElseThrow().getQuantity()).isEqualTo(10);
    assertThat(stockHistoryRepository.count()).isEqualTo(2);
  }
}
