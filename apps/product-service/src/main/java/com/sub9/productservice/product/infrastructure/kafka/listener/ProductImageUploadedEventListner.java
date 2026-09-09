package com.sub9.productservice.product.infrastructure.kafka.listener;

import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.product.application.command.service.ProductImageCommandService;
import com.sub9.productservice.product.application.event.ProductImageUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductImageUploadedEventListner {
  private final ProductImageCommandService productImageCommandService;

  @KafkaListener(
      topics = KafkaTopics.PRODUCT_IMAGE_UPLOADED,
      groupId = "${kafka.product-group-id}"
  )
  public void handleProducImageUploadEvent(ProductImageUploadedEvent event, Acknowledgment ack) {
    try {
      productImageCommandService.resizeImage(event.imageId(), event.productId(), event.originalKey());
      
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[ERROR] 이미지 리사이징 작업 실패 : ", e);
      // TODO : DLQ 적용 시 실패 메시지를 별도 토픽으로 발행 에러 핸들러로 뺴기
      throw new IllegalStateException("이미지 리사이징 작업 실패");
    }
  }
}
