package com.sub9.orderservice.order.infrastructure.kafka;

import com.sub9.common.kafka.event.OrderNotificationEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderNotificationEvent event) {
        // TODO: 지금은 발행 실패는 로그만 기록합니다. 추후에 Outbox로 전환 예정입니다.
        try {
            String payload = jsonMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.ORDER_NOTIFICATION, event.referenceId().toString(), payload)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.error("주문 알림 이벤트 발행 실패: eventId={}, orderId={}", event.eventId(), event.referenceId(), exception);
                        }
                    });
        } catch (Exception exception) {
            log.error("주문 알림 이벤트 발행 준비 실패: eventId={}, orderId={}", event.eventId(), event.referenceId(), exception);
        }
    }
}
