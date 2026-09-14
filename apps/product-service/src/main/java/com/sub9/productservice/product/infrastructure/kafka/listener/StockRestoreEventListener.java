package com.sub9.productservice.product.infrastructure.kafka.listener;

import com.sub9.common.kafka.event.StockRestoreEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.product.application.command.dto.stock.RestoreStockCommand;
import com.sub9.productservice.product.application.command.service.StockCommandService;
import com.sub9.productservice.product.application.port.in.stock.OrderStockUseCase;
import com.sub9.productservice.product.domain.model.StockHistoryReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockRestoreEventListener {
  private final OrderStockUseCase orderStockUseCase;

  @KafkaListener(topics = KafkaTopics.STOCK_RESTORE, groupId = "${kafka.product-group-id}")
  public void handleStockRestoreEvent(StockRestoreEvent event, Acknowledgment ack) {
    try {
      RestoreStockCommand command =
          new RestoreStockCommand(
              event.orderId(),
              event.items().stream()
                  .map(item -> new RestoreStockCommand.Item(item.skuId(), item.quantity()))
                  .toList(),
              StockHistoryReason.valueOf(event.reason()));

     orderStockUseCase.restore(command);

      ack.acknowledge();
    } catch (Exception e) {
      log.error("[ERROR] 재고 복구 작업 실패 : ", e);
      // TODO : DLQ 적용 시 실패 메시지를 별도 토픽으로 발행 에러 핸들러로 뺴기
      throw new IllegalStateException("재고 복구 작업 실패");
    }
  }
}
