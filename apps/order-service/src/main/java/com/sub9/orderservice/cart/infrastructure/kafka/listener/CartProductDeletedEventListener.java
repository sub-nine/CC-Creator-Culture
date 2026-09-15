package com.sub9.orderservice.cart.infrastructure.kafka.listener;

import com.sub9.common.kafka.event.ProductDeletedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.orderservice.cart.application.port.in.CartCleanupUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartProductDeletedEventListener {
  private final CartCleanupUseCase cartCleanupUseCase;

  @KafkaListener(topics = KafkaTopics.PRODUCT_DELETED, groupId = "cart-group")
  public void handleProductDeleted(ProductDeletedEvent event, Acknowledgment ack) {
    try {
      cartCleanupUseCase.cleanupByProductId(event.productId());

      ack.acknowledge();
    } catch (Exception e) {
      log.error("[ERROR] 장바구니 정리 작업 실패 : ", e);
      // TODO : DLQ 적용 시 실패 메시지를 별도 토픽으로 발행 에러 핸들러로 뺴기
      throw new IllegalStateException("장바구니 정리 작업 실패", e);
    }
  }
}
