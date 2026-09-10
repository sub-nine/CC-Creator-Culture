package com.sub9.productservice.category.infrastructure.messaging.consumer;

import com.sub9.common.kafka.event.ProductCreatedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.application.command.port.in.AddHashtagsToProductUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCreatedEventConsumer {
    private final AddHashtagsToProductUseCase addHashtagsToProductUseCase;

    @KafkaListener(topics = KafkaTopics.PRODUCT_CREATED, groupId = "${kafka.leaderboard-group-id}")
    public void consume(ProductCreatedEvent event, Acknowledgment ack) {
        try {
            log.info("[KAFKA] 상품 등록 이벤트 수신 - event: {}", event);

            addHashtagsToProductUseCase.addHashtagsToProduct(event.productId(), event.hashTags());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("[ERROR] 상품 해시태그 등록 실패 {}", e);
            // TODO : DLQ 적용 시 실패 메시지를 별도 토픽으로 발행 에러 핸들러로 빼기
            throw new IllegalStateException("상품 해시태그 등록 실패");
        }
    }
}
