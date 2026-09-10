package com.sub9.productservice.category.infrastructure.messaging.scheduler;

import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEventType;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxStatus;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.OutboxEventJpaRepository;
import com.sub9.productservice.support.AbstractKafkaIntegrationTest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@DisplayName("OutboxRelay - 통합 테스트")
class OutboxRelayIntegrationTest extends AbstractKafkaIntegrationTest {

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("PENDING 아웃박스 이벤트를 실제 Kafka로 발행하고 PUBLISHED로 전이한다")
    void publishPending_realKafka_publishesAndMarksPublished() throws Exception {
        // Given
        UUID hashtagId = UUID.randomUUID();
        String payload = "{\"hashtagId\":\"" + hashtagId + "\"}";
        OutboxEvent event = outboxEventJpaRepository.save(OutboxEvent.pending(OutboxEventType.HASHTAG_CREATED, payload));

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(embeddedKafka, "outbox-relay-test-group", true);
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.HASHTAG_CREATED);

        try {
            // When
            outboxRelay.publishPending();

            // Then - 실제 Kafka 토픽에서 우리 이벤트의 메시지를 찾는다(다른 테스트가 남긴 메시지가 섞여 있어도 무방)
            ConsumerRecord<String, String> matchingRecord = null;
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
            while (matchingRecord == null && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.key().equals(hashtagId.toString())) {
                        matchingRecord = record;
                        break;
                    }
                }
            }

            assertThat(matchingRecord).as("hashtagId=%s 키를 가진 메시지가 발행돼야 한다", hashtagId).isNotNull();
            assertThat(matchingRecord.value()).isEqualTo(payload);
        } finally {
            consumer.close();
        }

        // 발행 콜백은 별도 트랜잭션에서 비동기로 커밋되므로 상태 전이는 폴링으로 확인한다
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            OutboxEvent persisted = outboxEventJpaRepository.findById(event.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(persisted.getPublishedAt()).isNotNull();
        });
    }
}
