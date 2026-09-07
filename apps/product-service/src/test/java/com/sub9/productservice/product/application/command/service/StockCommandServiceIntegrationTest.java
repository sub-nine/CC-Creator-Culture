package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.productservice.product.application.command.dto.stock.AdjustStockCommand;
import com.sub9.productservice.product.application.command.dto.stock.DeductStockCommand;
import com.sub9.productservice.product.application.command.dto.stock.RestoreStockCommand;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.domain.model.StockHistory;
import com.sub9.productservice.product.domain.model.StockHistoryReason;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.sku.SkuCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.stock.StockCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.stock.StockHistoryCommandJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@DisplayName("StockCommandService - 통합 테스트")
class StockCommandServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired StockCommandService stockCommandService;
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired SkuCommandJpaRepository skuRepository;
  @Autowired StockCommandJpaRepository stockRepository;
  @Autowired StockHistoryCommandJpaRepository stockHistoryRepository;
  @Autowired EntityManager entityManager;

  private final UUID creatorId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final Instant previousUpdatedAt = Instant.parse("2000-01-01T00:00:00Z");
  private Stock firstStock;
  private Stock secondStock;

  @BeforeEach
  void setUp() {
    Product product = productRepository.save(Product.create(creatorId, "말랑이", "말랑이 설명"));
    Sku firstSku = skuRepository.save(Sku.create(product.getId(), "핑크", 10000L, true));
    Sku secondSku = skuRepository.save(Sku.create(product.getId(), "블루", 12000L, false));
    firstStock = stockRepository.save(Stock.create(firstSku.getId(), 10));
    secondStock = stockRepository.save(Stock.create(secondSku.getId(), 20));
    entityManager.flush();

    entityManager
        .createQuery("UPDATE Stock s SET s.updatedAt = :updatedAt WHERE s.id IN :ids")
        .setParameter("updatedAt", previousUpdatedAt)
        .setParameter("ids", List.of(firstStock.getId(), secondStock.getId()))
        .executeUpdate();
    entityManager.clear();
  }

  @Nested
  @DisplayName("재고 조정 테스트")
  class AdjustTests {
    @ParameterizedTest
    @ValueSource(ints = {5, -10})
    @DisplayName("창작자가 재고를 조정하면 수량, 수정 시각과 조정 이력이 저장된다.")
    void adjust_success(int quantity) {
      // given
      AdjustStockCommand command =
          new AdjustStockCommand(creatorId, firstStock.getSkuId(), quantity);

      // when
      stockCommandService.adjust(command);
      flushAndClear();

      // then
      assertStock(firstStock, 10 + quantity);
      assertThat(histories(firstStock))
          .singleElement()
          .satisfies(
              history -> {
                assertThat(history.getOrderId()).isNull();
                assertThat(history.getQuantity()).isEqualTo(quantity);
                assertThat(history.getReason()).isEqualTo(StockHistoryReason.CREATOR_ADJUSTMENT);
                assertThat(history.getCreatedAt()).isNotNull();
              });
    }

    @Test
    @DisplayName("창작자의 반복 조정은 각각 반영하고 이력을 남긴다.")
    void adjust_success_when_creator_adjusts_multiple_times() {
      // given
      AdjustStockCommand command = new AdjustStockCommand(creatorId, firstStock.getSkuId(), 5);

      // when
      stockCommandService.adjust(command);
      stockCommandService.adjust(command);
      flushAndClear();

      // then
      assertStock(firstStock, 20);
      assertThat(histories(firstStock))
          .hasSize(2)
          .allSatisfy(
              history -> {
                assertThat(history.getOrderId()).isNull();
                assertThat(history.getQuantity()).isEqualTo(5);
                assertThat(history.getReason()).isEqualTo(StockHistoryReason.CREATOR_ADJUSTMENT);
              });
    }
  }

  @Nested
  @DisplayName("재고 차감 테스트")
  class DeductTests {
    @Test
    @DisplayName("여러 SKU의 재고를 차감하고 SKU별 주문 이력을 저장한다.")
    void deduct_success() {
      // given
      DeductStockCommand command = deductCommand(orderId);

      // when
      stockCommandService.deduct(command);
      flushAndClear();

      // then
      assertStock(firstStock, 7);
      assertStock(secondStock, 0);
      assertOrderHistory(firstStock, StockHistoryReason.ORDER, 3);
      assertOrderHistory(secondStock, StockHistoryReason.ORDER, 20);
    }

    @Test
    @DisplayName("동일 주문을 재전송해도 재고와 이력이 중복 변경되지 않는다.")
    void deduct_success_when_same_order_is_retried() {
      // given
      DeductStockCommand command = deductCommand(orderId);
      stockCommandService.deduct(command);
      flushAndClear();

      // when
      stockCommandService.deduct(command);
      flushAndClear();

      // then
      assertStock(firstStock, 7);
      assertStock(secondStock, 0);
      assertOrderHistory(firstStock, StockHistoryReason.ORDER, 3);
      assertOrderHistory(secondStock, StockHistoryReason.ORDER, 20);
    }

    @Test
    @DisplayName("같은 SKU도 주문이 다르면 각각 차감한다.")
    void deduct_success_when_orders_are_different() {
      // given
      UUID secondOrderId = UUID.randomUUID();
      List<DeductStockCommand.Item> items =
          List.of(new DeductStockCommand.Item(firstStock.getSkuId(), 3));

      // when
      stockCommandService.deduct(new DeductStockCommand(orderId, items));
      stockCommandService.deduct(new DeductStockCommand(secondOrderId, items));
      flushAndClear();

      // then
      assertStock(firstStock, 4);
      assertThat(histories(firstStock))
          .hasSize(2)
          .extracting(StockHistory::getOrderId)
          .containsExactlyInAnyOrder(orderId, secondOrderId);
    }
  }

  @Nested
  @DisplayName("재고 복구 테스트")
  class RestoreTests {
    @Test
    @DisplayName("차감된 여러 SKU의 재고를 복구하고 복구 사유를 이력에 저장한다.")
    void restore_success() {
      // given
      StockHistoryReason reason = StockHistoryReason.ORDER_CANCEL;
      stockCommandService.deduct(deductCommand(orderId));
      flushAndClear();
      // 차감 시각과 별개로 복구 쿼리 자체의 시각 갱신을 검증한다.
      entityManager
          .createQuery("UPDATE Stock s SET s.updatedAt = :updatedAt WHERE s.id IN :ids")
          .setParameter("updatedAt", previousUpdatedAt)
          .setParameter("ids", List.of(firstStock.getId(), secondStock.getId()))
          .executeUpdate();
      entityManager.clear();

      // when
      stockCommandService.restore(restoreCommand(reason));
      flushAndClear();

      // then
      assertStock(firstStock, 10);
      assertStock(secondStock, 20);
      assertThat(histories(firstStock)).hasSize(2);
      assertThat(histories(secondStock)).hasSize(2);
      assertOrderHistory(firstStock, reason, 3);
      assertOrderHistory(secondStock, reason, 20);
    }

    @Test
    @DisplayName("동일 복구 요청을 재전송해도 재고와 이력을 한 번만 복구한다.")
    void restore_success_when_same_request_is_retried() {
      // given
      stockCommandService.deduct(deductCommand(orderId));
      RestoreStockCommand command = restoreCommand(StockHistoryReason.ORDER_CANCEL);
      stockCommandService.restore(command);
      flushAndClear();

      // when
      stockCommandService.restore(command);
      flushAndClear();

      // then
      assertStock(firstStock, 10);
      assertStock(secondStock, 20);
      assertThat(histories(firstStock)).hasSize(2);
      assertThat(histories(secondStock)).hasSize(2);
      assertOrderHistory(firstStock, StockHistoryReason.ORDER_CANCEL, 3);
      assertOrderHistory(secondStock, StockHistoryReason.ORDER_CANCEL, 20);
    }
  }

  private DeductStockCommand deductCommand(UUID id) {
    return new DeductStockCommand(
        id,
        List.of(
            new DeductStockCommand.Item(firstStock.getSkuId(), 3),
            new DeductStockCommand.Item(secondStock.getSkuId(), 20)));
  }

  private RestoreStockCommand restoreCommand(StockHistoryReason reason) {
    return new RestoreStockCommand(
        orderId,
        List.of(
            new RestoreStockCommand.Item(firstStock.getSkuId(), 3),
            new RestoreStockCommand.Item(secondStock.getSkuId(), 20)),
        reason);
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private void assertStock(Stock original, int quantity) {
    Stock stock = stockRepository.findById(original.getId()).orElseThrow();
    assertThat(stock.getQuantity()).isEqualTo(quantity);
    assertThat(stock.getUpdatedAt()).isAfter(previousUpdatedAt);
  }

  private List<StockHistory> histories(Stock stock) {
    return stockHistoryRepository.findAll().stream()
        .filter(history -> history.getSkuId().equals(stock.getSkuId()))
        .toList();
  }

  private void assertOrderHistory(Stock stock, StockHistoryReason reason, int quantity) {
    assertThat(histories(stock))
        .filteredOn(history -> history.getReason() == reason)
        .singleElement()
        .satisfies(
            history -> {
              assertThat(history.getOrderId()).isEqualTo(orderId);
              assertThat(history.getQuantity()).isEqualTo(quantity);
              assertThat(history.getCreatedAt()).isNotNull();
            });
  }
}
