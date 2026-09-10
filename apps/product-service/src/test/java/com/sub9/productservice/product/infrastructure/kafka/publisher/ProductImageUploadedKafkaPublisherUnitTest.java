package com.sub9.productservice.product.infrastructure.kafka.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.product.application.event.ProductImageUploadedEvent;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImageUploadedKafkaPublisher - 단위 테스트")
class ProductImageUploadedKafkaPublisherUnitTest {
  @Mock KafkaTemplate<String, String> kafkaTemplate;
  private final JsonMapper jsonMapper = new JsonMapper();
  private ProductImageUploadedKafkaPublisher publisher;
  private final ProductImageUploadedEvent event =
      new ProductImageUploadedEvent(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "original/image");

  @BeforeEach
  void setUp() {
    publisher = new ProductImageUploadedKafkaPublisher(kafkaTemplate, jsonMapper);
  }

  @Test
  @DisplayName("이미지 처리에 필요한 식별자와 원본 키를 지정한 토픽으로 발행한다.")
  void publish_success() {
    // given
    given(kafkaTemplate.send(anyString(), anyString()))
        .willReturn(CompletableFuture.completedFuture(null));

    // when
    publisher.publish(event).join();

    // then
    var payload = ArgumentCaptor.forClass(String.class);

    verify(kafkaTemplate).send(eq(KafkaTopics.PRODUCT_IMAGE_UPLOADED), payload.capture());
    assertThat(jsonMapper.readValue(payload.getValue(), ProductImageUploadedEvent.class))
        .isEqualTo(event);
  }

  @Test
  @DisplayName("Kafka 전송 실패 시 반환된 Future도 실패한다.")
  void publish_fails_when_kafka_send_fails() {
    // given
    RuntimeException failure = new RuntimeException("Kafka unavailable");
    given(kafkaTemplate.send(anyString(), anyString()))
        .willReturn(CompletableFuture.failedFuture(failure));

    // when & then
    assertThatThrownBy(() -> publisher.publish(event).join())
        .isInstanceOf(CompletionException.class)
        .hasCause(failure);
  }
}
