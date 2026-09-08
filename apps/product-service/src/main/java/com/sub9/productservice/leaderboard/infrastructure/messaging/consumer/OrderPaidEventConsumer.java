package com.sub9.productservice.leaderboard.infrastructure.messaging.consumer;

import com.sub9.common.kafka.event.OrderPaidEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.leaderboard.application.model.ProductQuantity;
import com.sub9.productservice.leaderboard.application.port.in.RecordOrderScoreUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventConsumer {
    private final RecordOrderScoreUseCase recordOrderScoreUseCase;

    @KafkaListener(topics = KafkaTopics.ORDER_PAID, groupId = "${kafka.leaderboard-group-id}")
    public void consume(OrderPaidEvent event, Acknowledgment ack) {
        try {
            log.info("[KAFKA] 주문 결제 이벤트 수신 - orderId: {}, products: {}",
                    event.orderId(), event.productQuantities());
            List<ProductQuantity> productQuantities = event.productQuantities().stream().map((quantity) ->
                    new ProductQuantity(
                            quantity.productId(),
                            quantity.quantity()
                    )
            ).toList();
            recordOrderScoreUseCase.recordOrderScore(event.orderId(), productQuantities);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[ERROR] 주문 리더보드 점수 반영 실패 {}", e);
            // TODO : DLQ 적용 시 실패 메시지를 별도 토픽으로 발행 에러 핸들러로 빼기
            throw new IllegalStateException("주문 리더보드 점수 반영 실패");
        }
    }
}
