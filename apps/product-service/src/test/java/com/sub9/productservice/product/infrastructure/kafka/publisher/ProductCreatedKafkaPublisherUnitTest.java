package com.sub9.productservice.product.infrastructure.kafka.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sub9.common.kafka.event.ProductCreatedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCreatedKafkaPublisher - 단위 테스트")
class ProductCreatedKafkaPublisherUnitTest {

  @Mock KafkaTemplate<String, String> kafkaTemplate;

  private JsonMapper jsonMapper;
  private ProductCreatedKafkaPublisher publisher;

  @BeforeEach
  void setUp() {
    jsonMapper = new JsonMapper();
    publisher = new ProductCreatedKafkaPublisher(kafkaTemplate, jsonMapper);
  }

  @Test
  @DisplayName("상품 생성 이벤트를 발행하면 지정된 토픽과 상품 ID 키로 직렬화된 메시지를 전송한다")
  void
      when_product_created_event_is_published_publisher_sends_serialized_message_with_topic_and_key()
          throws Exception {
    // given
    UUID productId = UUID.randomUUID();
    UUID creatorId = UUID.randomUUID();

    ProductCreatedEvent event =
        new ProductCreatedEvent(productId, creatorId, "왁뿌볼", "상품 설명", List.of("핑크 M", "핑크 L"));

    given(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .willReturn(CompletableFuture.completedFuture(null));

    // when
    publisher.publish(event);

    // then
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

    verify(kafkaTemplate)
        .send(
            org.mockito.ArgumentMatchers.eq(KafkaTopics.PRODUCT_CREATED),
            org.mockito.ArgumentMatchers.eq(productId.toString()),
            payloadCaptor.capture());

    JsonNode payload = jsonMapper.readTree(payloadCaptor.getValue());
    assertThat(payload.get("productId").asString()).isEqualTo(productId.toString());
    assertThat(payload.get("creatorId").asString()).isEqualTo(creatorId.toString());
    assertThat(payload.get("name").asString()).isEqualTo("왁뿌볼");
    assertThat(payload.get("content").asString()).isEqualTo("상품 설명");
    assertThat(payload.get("hashTags").get(0).asString()).isEqualTo("핑크 M");
    assertThat(payload.get("hashTags").get(1).asString()).isEqualTo("핑크 L");
  }

  // TODO : 추후 실패 테스트 케이스 추가
}
