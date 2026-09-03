package com.sub9.productservice.product.infrastructure.kafka.publisher;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.product.application.event.ProductCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCreatedKafkaPublisher {
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final JsonMapper jsonMapper;

  @TransactionalEventListener
  public void publish(ProductCreatedEvent event) {
    try {
      String payload = jsonMapper.writeValueAsString(event);

      kafkaTemplate
          .send(KafkaTopics.PRODUCT_CREATED, event.productId().toString(), payload)
          .whenComplete(
              (result, exception) -> {
                if (exception != null) {
                  log.error("[KAFKA] 메시지 발행 실패 {}", exception.getMessage());
                  // TODO : 메시지 유실 방지는 추후에 구현
                  return;
                }
                log.info("[KAFKA] 메시지 발행 성공");
              });
    } catch (JacksonException e) {
      log.error("[KAFKA] 메시지 직렬화 실패 : ", e);
      // 추후 예외 수정
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
