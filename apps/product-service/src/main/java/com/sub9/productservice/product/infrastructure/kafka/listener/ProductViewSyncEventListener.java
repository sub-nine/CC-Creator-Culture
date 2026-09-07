package com.sub9.productservice.product.infrastructure.kafka.listener;

import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.product.application.command.dto.IncrementDailyViewCountsCommand;
import com.sub9.productservice.product.application.command.service.ProductViewCountCommandService;
import com.sub9.common.kafka.event.ProductViewSyncEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductViewSyncEventListener {
  private final ProductViewCountCommandService productViewCountCommandService;

  @KafkaListener(topics = KafkaTopics.PRODUCT_VIEW_COUNT_SYNC, groupId = "${kafka.product-group-id}")
  public void handleProductViewSyncEvent(ProductViewSyncEvent event, Acknowledgment ack) {
    try {
      IncrementDailyViewCountsCommand command =
          new IncrementDailyViewCountsCommand(
              event.eventId(),
              event.viewDate(),
              event.productViewCounts().stream()
                  .map(
                      count ->
                          new IncrementDailyViewCountsCommand.ViewCount(
                              count.productId(), count.viewCount()))
                  .toList());

      productViewCountCommandService.incrementDailyViewCounts(command);

      ack.acknowledge();
    } catch (Exception e) {
      log.error("[ERROR] 조회수 배치 작업 실패 {}", e);
      // TODO : DLQ 적용 시 실패 메시지를 별도 토픽으로 발행 에러 핸들러로 뺴기
      throw new IllegalStateException("배치 작업 실패");
    }
  }
}
