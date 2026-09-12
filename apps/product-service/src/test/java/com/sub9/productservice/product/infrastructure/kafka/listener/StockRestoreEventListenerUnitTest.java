package com.sub9.productservice.product.infrastructure.kafka.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.kafka.event.StockRestoreEvent;
import com.sub9.productservice.product.application.command.dto.stock.RestoreStockCommand;
import com.sub9.productservice.product.application.port.in.stock.OrderStockUseCase;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.StockHistoryReason;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockRestoreEventListener - 단위 테스트")
class StockRestoreEventListenerUnitTest {
  @Mock private OrderStockUseCase orderStockUseCase;
  @Mock private Acknowledgment acknowledgment;
  @InjectMocks private StockRestoreEventListener stockRestoreEventListener;

  private final UUID orderId = UUID.randomUUID();
  private final UUID skuId = UUID.randomUUID();

  @Test
  @DisplayName("재고 복구에 성공하고 ACK를 호출한다.")
  void handleStockRestoreEvent_success() {
    // given
    UUID secondSkuId = UUID.randomUUID();
    StockRestoreEvent event =
        new StockRestoreEvent(
            orderId,
            List.of(
                new StockRestoreEvent.Item(skuId, 3), new StockRestoreEvent.Item(secondSkuId, 2)),
            "ORDER_CANCEL");
    RestoreStockCommand command =
        new RestoreStockCommand(
            orderId,
            List.of(
                new RestoreStockCommand.Item(skuId, 3),
                new RestoreStockCommand.Item(secondSkuId, 2)),
            StockHistoryReason.ORDER_CANCEL);

    // when
    stockRestoreEventListener.handleStockRestoreEvent(event, acknowledgment);

    // then
    InOrder inOrder = inOrder(orderStockUseCase, acknowledgment);
    inOrder.verify(orderStockUseCase).restore(command);
    inOrder.verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("재고 복구 실패 시 SKU_NOT_FOUND 예외를 반환하고 ACK를 호출하지 않는다.")
  void handleStockRestoreEvent_fails_when_restore_fails() {
    // given
    StockRestoreEvent event =
        new StockRestoreEvent(
            orderId, List.of(new StockRestoreEvent.Item(skuId, 3)), "ORDER_CANCEL");
    willThrow(new BusinessException(ProductErrorCode.SKU_NOT_FOUND))
        .given(orderStockUseCase)
        .restore(any());

    // when & then
    assertThatThrownBy(
            () -> stockRestoreEventListener.handleStockRestoreEvent(event, acknowledgment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("재고 복구 작업 실패");
    verifyNoInteractions(acknowledgment);
  }
}
