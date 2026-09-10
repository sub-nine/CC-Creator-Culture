package com.sub9.productservice.category.infrastructure.messaging.consumer;

import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.application.command.port.in.LinkHashtagToCategoryUseCase;
import com.sub9.productservice.category.domain.event.HashtagCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HashtagCreatedEventConsumer {
    private final LinkHashtagToCategoryUseCase linkHashtagToCategoryUseCase;

    @KafkaListener(topics = KafkaTopics.HASHTAG_CREATED, groupId = "${kafka.category-group-id}")
    public void consume(HashtagCreatedEvent event, Acknowledgment ack) {
        try {
            log.info("[KAFKA] 해시태그 등록 이벤트 수신 - event: {}", event);

            linkHashtagToCategoryUseCase.tryLink(event.hashtagId());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("[ERROR] 해시태그-카테고리 연결 시도 실패 {}", e);
            // TODO : DLQ 적용 시 실패 메시지를 별도 토픽으로 발행 에러 핸들러로 빼기
            throw new IllegalStateException("해시태그-카테고리 연결 시도 실패");
        }
    }
}
