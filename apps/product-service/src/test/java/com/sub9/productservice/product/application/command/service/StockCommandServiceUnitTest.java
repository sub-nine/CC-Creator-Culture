package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.command.dto.stock.AdjustStockCommand;
import com.sub9.productservice.product.application.command.dto.stock.DeductStockCommand;
import com.sub9.productservice.product.application.command.dto.stock.RestoreStockCommand;
import com.sub9.productservice.product.application.query.repository.ProductQueryRepository;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.StockHistoryReason;
import com.sub9.productservice.product.domain.repository.StockCommandRepository;
import com.sub9.productservice.product.domain.repository.StockHistoryCommandRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockCommandService - 단위 테스트")
class StockCommandServiceUnitTest {
  @Mock private StockCommandRepository stockCommandRepository;
  @Mock private StockHistoryCommandRepository stockHistoryCommandRepository;
  @Mock private ProductQueryRepository productQueryRepository;
  @InjectMocks private StockCommandService stockCommandService;

  private final UUID creatorId = UUID.randomUUID();
  private final UUID skuId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();

  @Nested
  @DisplayName("재고 조정 테스트")
  class AdjustTests {
    @Test
    @DisplayName("SKU 소유자가 아니면 PRODUCT_ACCESS_DENIED 예외가 발생한다.")
    void adjust_fails_when_creator_is_not_owner() {
      // given
      AdjustStockCommand command = new AdjustStockCommand(creatorId, skuId, 5);
      given(productQueryRepository.existsSkuOwnedByCreatorId(creatorId, skuId)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> stockCommandService.adjust(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_ACCESS_DENIED.message());
      verifyNoInteractions(stockCommandRepository, stockHistoryCommandRepository);
    }

    @Test
    @DisplayName("변경 수량이 0이면 INVALID_STOCK_ADJUSTMENT 예외가 발생한다.")
    void adjust_fails_when_quantity_is_zero() {
      // given
      AdjustStockCommand command = new AdjustStockCommand(creatorId, skuId, 0);
      given(productQueryRepository.existsSkuOwnedByCreatorId(creatorId, skuId)).willReturn(true);

      // when & then
      assertThatThrownBy(() -> stockCommandService.adjust(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.INVALID_STOCK_ADJUSTMENT.message());
      verifyNoInteractions(stockCommandRepository, stockHistoryCommandRepository);
    }

    @Test
    @DisplayName("조정할 재고가 부족하면 이력을 저장하지 않고 INSUFFICIENT_STOCK 예외가 발생한다.")
    void adjust_fails_when_stock_is_insufficient() {
      // given
      AdjustStockCommand command = new AdjustStockCommand(creatorId, skuId, -11);

      given(productQueryRepository.existsSkuOwnedByCreatorId(creatorId, skuId)).willReturn(true);
      given(stockCommandRepository.adjustStock(skuId, -11)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> stockCommandService.adjust(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.INSUFFICIENT_STOCK.message());
      verifyNoInteractions(stockHistoryCommandRepository);
    }
  }

  @Nested
  @DisplayName("재고 차감 테스트")
  class DeductTests {
    @Test
    @DisplayName("재고가 부족하면 INSUFFICIENT_STOCK 예외가 발생하고 다음 SKU를 처리하지 않는다.")
    void deduct_fails_when_stock_is_insufficient() {
      // given
      UUID nextSkuId = UUID.randomUUID();
      DeductStockCommand command =
          new DeductStockCommand(
              orderId,
              List.of(
                  new DeductStockCommand.Item(skuId, 11),
                  new DeductStockCommand.Item(nextSkuId, 2)));

      given(stockHistoryCommandRepository.insertIfAbsent(any())).willReturn(true);
      given(stockCommandRepository.decreaseStock(skuId, 11)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> stockCommandService.deduct(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.INSUFFICIENT_STOCK.message());
      verify(stockCommandRepository, never()).decreaseStock(nextSkuId, 2);
    }
  }

  @Nested
  @DisplayName("재고 복구 테스트")
  class RestoreTests {
    @Test
    @DisplayName("복구할 재고가 없으면 SKU_NOT_FOUND 예외가 발생하고 다음 SKU를 처리하지 않는다.")
    void restore_fails_when_stock_not_found() {
      // given
      UUID nextSkuId = UUID.randomUUID();
      RestoreStockCommand command =
          new RestoreStockCommand(
              orderId,
              List.of(
                  new RestoreStockCommand.Item(skuId, 3),
                  new RestoreStockCommand.Item(nextSkuId, 2)),
              StockHistoryReason.ORDER_CANCEL);

      given(stockHistoryCommandRepository.insertIfAbsent(any())).willReturn(true);
      given(stockCommandRepository.increaseStock(skuId, 3)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> stockCommandService.restore(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.SKU_NOT_FOUND.message());
      verify(stockCommandRepository, never()).increaseStock(nextSkuId, 2);
    }
  }
}
