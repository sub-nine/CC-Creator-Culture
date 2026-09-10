package com.sub9.productservice.category.infrastructure.messaging.publisher;

import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.domain.event.HashtagCreatedEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEventType;
import com.sub9.productservice.common.config.kafka.KafkaProducerConfig;
import com.sub9.productservice.common.config.kafka.KafkaProperties;
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

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(HashtagCreatedEventOutboxPublisherIntegrationTest.TestConfig.class)
@DisplayName("HashtagCreatedEventOutboxPublisher - 통합 테스트")
class HashtagCreatedEventOutboxPublisherIntegrationTest {

    @Autowired
    private HashtagCreatedEventOutboxPublisher publisher;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("아웃박스 이벤트를 발행하면 Kafka에서 해시태그 ID를 키로 하는 동일한 페이로드를 수신한다")
    void publish_success() throws Exception {
        // Given
        UUID hashtagId = UUID.randomUUID();
        String payload = jsonMapper.writeValueAsString(new HashtagCreatedEvent(hashtagId));
        OutboxEvent outboxEvent = OutboxEvent.pending(OutboxEventType.HASHTAG_CREATED, payload);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(embeddedKafka, "test-group", true);
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.HASHTAG_CREATED);

        try {
            // When
            publisher.publish(outboxEvent).get();

            // Then
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    consumer, KafkaTopics.HASHTAG_CREATED, Duration.ofSeconds(10));

            assertThat(record.key()).isEqualTo(hashtagId.toString());

            JsonNode receivedPayload = jsonMapper.readTree(record.value());
            assertThat(receivedPayload.get("hashtagId").asString()).isEqualTo(hashtagId.toString());
        } finally {
            consumer.close();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({KafkaProducerConfig.class, HashtagCreatedEventOutboxPublisher.class})
    static class TestConfig {

        @Bean(name = EmbeddedKafkaBroker.BEAN_NAME)
        EmbeddedKafkaBroker embeddedKafkaBroker() {
            return new EmbeddedKafkaKraftBroker(1, 1, KafkaTopics.HASHTAG_CREATED);
        }

        @Bean
        KafkaProperties kafkaProperties(EmbeddedKafkaBroker embeddedKafkaBroker) {
            return new KafkaProperties(embeddedKafkaBroker.getBrokersAsString(), "earliest");
        }

        @Bean
        JsonMapper jsonMapper() {
            return new JsonMapper();
        }
    }
}
