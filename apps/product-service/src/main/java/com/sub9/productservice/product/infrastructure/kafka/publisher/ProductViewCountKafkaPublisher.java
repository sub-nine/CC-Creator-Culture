package com.sub9.productservice.product.infrastructure.kafka.publisher;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.common.kafka.event.ProductViewSyncEvent;
import com.sub9.productservice.product.application.port.ProductViewCountPublisher;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewCountKafkaPublisher implements ProductViewCountPublisher {
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final JsonMapper jsonMapper;

  @Override
  public CompletableFuture<Void> publish(ProductViewSyncEvent event) {
    try {
      String payload = jsonMapper.writeValueAsString(event);

      return kafkaTemplate
          .send(KafkaTopics.PRODUCT_VIEW_COUNT_SYNC, payload)
          .thenAccept(result -> log.info("[KAFKA] 메시지 발행 성공 topic = {}, eventId = {}", KafkaTopics.PRODUCT_VIEW_COUNT_SYNC, event.eventId()))
          .whenComplete(
              (result, exception) -> {
                if (exception != null) {
                  log.error("[KAFKA] 메시지 발행 실패", exception);
                  // TODO : 메시지 유실 방지는 추후에 구현
                }
              });
    } catch (JacksonException e) {
      log.error("[KAFKA] 메시지 직렬화 실패 : ", e);
      // 추후 예외 수정
      return CompletableFuture.failedFuture(
          new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }
  }
}
