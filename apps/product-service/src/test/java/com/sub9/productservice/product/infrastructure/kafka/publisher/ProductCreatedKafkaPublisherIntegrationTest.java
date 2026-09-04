package com.sub9.productservice.product.infrastructure.kafka.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.common.kafka.event.ProductCreatedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.common.config.kafka.KafkaProducerConfig;
import com.sub9.productservice.common.config.kafka.KafkaProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitConfig(ProductCreatedKafkaPublisherIntegrationTest.TestConfig.class)
@DisplayName("ProductCreatedKafkaPublisher - 통합 테스트")
class ProductCreatedKafkaPublisherIntegrationTest {

  @Autowired ProductCreatedKafkaPublisher kafkaPublisher;
  @Autowired EmbeddedKafkaBroker embeddedKafka;
  @Autowired JsonMapper jsonMapper;

  @Test
  @DisplayName("상품 생성 이벤트를 발행하면 Kafka에서 동일한 키와 메시지를 수신한다")
  void when_product_created_event_is_published_kafka_receives_message_with_same_key_and_payload()
      throws Exception {
    // given
    UUID productId = UUID.randomUUID();
    UUID creatorId = UUID.randomUUID();

    ProductCreatedEvent event =
        new ProductCreatedEvent(productId, creatorId, "왁뿌볼", "왁뿌볼 설명", List.of("왁뿌볼", "말랑이"));

    Map<String, Object> consumerProperties =
        KafkaTestUtils.consumerProps(embeddedKafka, "test-group", true);

    Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<>(
                consumerProperties, new StringDeserializer(), new StringDeserializer())
            .createConsumer();

    embeddedKafka.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.PRODUCT_CREATED);

    try {
      // when
      kafkaPublisher.publish(event);

      // then
      ConsumerRecord<String, String> record =
          KafkaTestUtils.getSingleRecord(
              consumer, KafkaTopics.PRODUCT_CREATED, Duration.ofSeconds(10));

      assertThat(record.key()).isEqualTo(productId.toString());

      JsonNode payload = jsonMapper.readTree(record.value());
      assertThat(payload.get("productId").asString()).isEqualTo(productId.toString());
      assertThat(payload.get("creatorId").asString()).isEqualTo(creatorId.toString());
      assertThat(payload.get("name").asString()).isEqualTo("왁뿌볼");
      assertThat(payload.get("content").asString()).isEqualTo("왁뿌볼 설명");
      assertThat(payload.get("hashTags").get(0).asString()).isEqualTo("왁뿌볼");
      assertThat(payload.get("hashTags").get(1).asString()).isEqualTo("말랑이");
    } finally {
      consumer.close();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  @Import({KafkaProducerConfig.class, ProductCreatedKafkaPublisher.class})
  static class TestConfig {

    @Bean(name = EmbeddedKafkaBroker.BEAN_NAME)
    EmbeddedKafkaBroker embeddedKafkaBroker() {
      return new EmbeddedKafkaKraftBroker(1, 1, KafkaTopics.PRODUCT_CREATED);
    }

    @Bean
    KafkaProperties kafkaProperties(EmbeddedKafkaBroker embeddedKafkaBroker) {
      return new KafkaProperties(
          embeddedKafkaBroker.getBrokersAsString(),
          "earliest",
          "product-test-group",
          "category-test-group");
    }

    @Bean
    JsonMapper jsonMapper() {
      return new JsonMapper();
    }
  }
}
