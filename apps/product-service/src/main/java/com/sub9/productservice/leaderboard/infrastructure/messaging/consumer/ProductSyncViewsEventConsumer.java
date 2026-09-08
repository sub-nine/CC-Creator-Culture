package com.sub9.productservice.leaderboard.infrastructure.messaging.consumer;

import com.sub9.common.kafka.event.ProductViewSyncEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.leaderboard.application.model.ProductViewCount;
import com.sub9.productservice.leaderboard.application.port.in.RecordProductViewScoreUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSyncViewsEventConsumer {
    private final RecordProductViewScoreUseCase recordProductViewScoreUseCase;

    @KafkaListener(topics = KafkaTopics.PRODUCT_VIEW_COUNT_SYNC, groupId = "${kafka.leaderboard-group-id}")
    public void consume(ProductViewSyncEvent event, Acknowledgment ack) {
        try {
            log.info("[KAFKA] 조회수 동기화 이벤트 수신 - eventId: {}", event.eventId());

            List<ProductViewCount> productViewCounts = event.productViewCounts().stream()
                    .map((productViewCount -> new ProductViewCount(
                            productViewCount.productId(),
                            productViewCount.viewCount()
                    ))).toList();

            recordProductViewScoreUseCase.recordProductViewScore(event.eventId(), productViewCounts);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[ERROR] 조회수 리더보드 점수 반영 실패 {}", e);
            // TODO : DLQ 적용 시 실패 메시지를 별도 토픽으로 발행 에러 핸들러로 빼기
            throw new IllegalStateException("조회수 리더보드 점수 반영 실패");
        }
    }
}
