package com.sub9.userservice.user.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sub9.common.kafka.event.UserDeletedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("사용자 탈퇴 이벤트 Kafka 발행")
class UserDeletedKafkaPublisherTest {

    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final JsonMapper jsonMapper = new JsonMapper();
    private final UserDeletedKafkaPublisher publisher =
            new UserDeletedKafkaPublisher(kafkaTemplate, jsonMapper);
    private final UserDeletedEvent event = new UserDeletedEvent(
            UUID.fromString("01994c9d-32c0-7000-8000-000000000001"),
            UUID.fromString("01994c9d-32c0-7000-8000-000000000002"),
            Instant.parse("2026-09-14T08:00:00Z"));

    @Test
    @DisplayName("user.deleted 토픽과 사용자 ID 키로 이벤트 JSON을 발행한다")
    void when_published_topic_key_and_json_match_event() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
                eq(KafkaTopics.USER_DELETED), eq(event.userId().toString()), payload.capture());
        assertThat(jsonMapper.readValue(payload.getValue(), UserDeletedEvent.class))
                .isEqualTo(event);
    }

    @Test
    @DisplayName("동기 전송에 실패해도 리스너 밖으로 예외를 전파하지 않는다")
    void when_send_throws_failure_does_not_escape_listener() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비동기 전송 실패를 처리할 때 예외를 던지지 않는다")
    void when_async_send_fails_completion_does_not_throw() {
        CompletableFuture<SendResult<String, String>> result = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(result);

        publisher.publish(event);

        assertThat(result.getNumberOfDependents()).isEqualTo(1);
        assertThatCode(() -> result.completeExceptionally(new IllegalStateException("send failed")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JSON 변환에 실패하면 메시지를 전송하지 않는다")
    void when_serialization_fails_message_is_not_sent() {
        JsonMapper brokenMapper = mock(JsonMapper.class);
        when(brokenMapper.writeValueAsString(event))
                .thenThrow(new IllegalArgumentException("invalid JSON"));

        assertThatCode(() -> new UserDeletedKafkaPublisher(kafkaTemplate, brokenMapper)
                .publish(event)).doesNotThrowAnyException();
        verifyNoInteractions(kafkaTemplate);
    }
}
