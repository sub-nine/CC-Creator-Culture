package com.sub9.productservice.category.infrastructure.messaging.consumer;

import com.sub9.common.kafka.event.CategoryCreatedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.application.command.port.in.CalculateCategoryVectorUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryCreatedEventConsumer {

    private final CalculateCategoryVectorUseCase calculateCategoryVectorUseCase;

    @KafkaListener(topics = KafkaTopics.CATEGORY_CREATED, groupId = "${kafka.category-group-id}")
    public void consume(CategoryCreatedEvent event, Acknowledgment ack) {
        try {
            log.info("[KAFKA] 카테고리 생성 이벤트 수신 - event: {}", event);

            calculateCategoryVectorUseCase.calculate(event.categoryId());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("[ERROR] 카테고리 벡터 계산 실패 {}", e);
            // TODO : DLQ 적용 시 실패 메시지를 별도 토픽으로 발행 에러 핸들러로 빼기
            throw new IllegalStateException("카테고리 벡터 계산 실패");
        }
    }
}
