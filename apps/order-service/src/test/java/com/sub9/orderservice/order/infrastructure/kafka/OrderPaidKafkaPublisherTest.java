package com.sub9.orderservice.order.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sub9.common.kafka.event.OrderPaidEvent;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("주문 결제 완료 이벤트 Kafka 발행")
class OrderPaidKafkaPublisherTest {

    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final JsonMapper mapper = new JsonMapper();
    private final OrderPaidKafkaPublisher publisher = new OrderPaidKafkaPublisher(kafka, mapper);
    private final OrderPaidEvent event = new OrderPaidEvent(UUID.randomUUID(),
            List.of(new OrderPaidEvent.ProductQuantity(UUID.randomUUID(), 5L)));

    @Test
    @DisplayName("지정된 토픽과 주문 ID 키로 이벤트 JSON을 발행한다")
    void when_published_topic_key_and_json_match_event() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("order.paid"), eq(event.orderId().toString()), payload.capture());
        assertThat(mapper.readValue(payload.getValue(), OrderPaidEvent.class)).isEqualTo(event);
    }

    @Test
    @DisplayName("동기 전송에 실패해도 리스너 밖으로 예외를 전파하지 않는다")
    void when_send_throws_failure_does_not_escape_listener() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비동기 전송 실패를 처리할 때 예외를 던지지 않는다")
    void when_async_send_fails_completion_does_not_throw() {
        CompletableFuture<SendResult<String, String>> result = new CompletableFuture<>();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(result);

        publisher.publish(event);

        assertThat(result.getNumberOfDependents()).isEqualTo(1);
        assertThatCode(() -> result.completeExceptionally(new IllegalStateException("send failed")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JSON 변환에 실패하면 메시지를 전송하지 않는다")
    void when_serialization_fails_message_is_not_sent() {
        JsonMapper brokenMapper = mock(JsonMapper.class);
        when(brokenMapper.writeValueAsString(event)).thenThrow(new IllegalArgumentException("invalid JSON"));

        assertThatCode(() -> new OrderPaidKafkaPublisher(kafka, brokenMapper).publish(event))
                .doesNotThrowAnyException();
        verifyNoInteractions(kafka);
    }
}
