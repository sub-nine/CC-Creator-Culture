package com.sub9.productservice.wishlist.infrastructure.kafka.listener;

import com.sub9.common.kafka.event.ProductDeletedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.wishlist.application.port.in.CleanupWishlistUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WishlistProductDeletedEventListener {
  private final CleanupWishlistUseCase cleanupWishlistUseCase;

  @KafkaListener(topics = KafkaTopics.PRODUCT_DELETED, groupId = "${kafka.wishlist-group-id}")
  public void handleProductDeleted(ProductDeletedEvent event, Acknowledgment ack) {
    try {
      cleanupWishlistUseCase.cleanUpWByProductId(event.productId());

      ack.acknowledge();
    } catch (Exception e) {
      log.error("[ERROR] 관심 상품 정리 작업 실패 : ", e);
      // TODO : DLQ 적용 시 실패 메시지를 별도 토픽으로 발행 에러 핸들러로 뺴기
      throw new IllegalStateException("관심 상품 정리 작업 실패");
    }
  }
}
