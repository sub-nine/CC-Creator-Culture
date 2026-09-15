package com.sub9.userservice.user.infrastructure.messaging.kafka;

import com.sub9.common.kafka.event.UserDeletedEvent;
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
public class UserDeletedKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(UserDeletedEvent event) {
        // 발행 실패는 로그만 기록
        try {
            String payload = jsonMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.USER_DELETED, event.userId().toString(), payload)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.error(
                                    "사용자 탈퇴 이벤트 발행 실패: topic={}, eventId={}, userId={}",
                                    KafkaTopics.USER_DELETED,
                                    event.eventId(),
                                    event.userId(),
                                    exception);
                        }
                    });
        } catch (Exception exception) {
            log.error(
                    "사용자 탈퇴 이벤트 발행 준비 실패: topic={}, eventId={}, userId={}",
                    KafkaTopics.USER_DELETED,
                    event.eventId(),
                    event.userId(),
                    exception);
        }
    }
}
