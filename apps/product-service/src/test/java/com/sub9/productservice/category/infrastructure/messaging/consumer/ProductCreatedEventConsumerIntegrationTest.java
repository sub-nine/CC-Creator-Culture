package com.sub9.productservice.category.infrastructure.messaging.consumer;

import com.sub9.common.kafka.event.ProductCreatedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.entity.HashtagProduct;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEventType;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagProductJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.OutboxEventJpaRepository;
import com.sub9.productservice.support.AbstractKafkaIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@DisplayName("ProductCreatedEventConsumer - 통합 테스트")
class ProductCreatedEventConsumerIntegrationTest extends AbstractKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private HashtagJpaRepository hashtagJpaRepository;

    @Autowired
    private HashtagProductJpaRepository hashtagProductJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private KafkaListenerEndpointRegistry listenerEndpointRegistry;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @BeforeEach
    void waitForPartitionAssignment() {
        MessageListenerContainer container = findContainerForTopic(KafkaTopics.PRODUCT_CREATED);
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
    }

    private MessageListenerContainer findContainerForTopic(String topic) {
        return listenerEndpointRegistry.getListenerContainers().stream()
                .filter(container -> Arrays.asList(container.getContainerProperties().getTopics()).contains(topic))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("리스너 컨테이너를 찾을 수 없음 - topic: " + topic));
    }

    @Test
    @DisplayName("PRODUCT_CREATED 이벤트를 수신하면 해시태그를 생성/연결하고, 신규 해시태그는 아웃박스에 기록한다")
    void consume_createsAndLinksHashtagsAndRecordsOutboxForNewHashtags() throws Exception {
        // Given
        UUID productId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();
        String rawHashtagName = "통합테스트태그" + suffix;
        String normalizedHashtagName = rawHashtagName.toUpperCase(Locale.ROOT);

        ProductCreatedEvent event = new ProductCreatedEvent(
                productId, UUID.randomUUID(), "상품" + suffix, "상품 설명", List.of(rawHashtagName));
        String payload = jsonMapper.writeValueAsString(event);

        // When
        kafkaTemplate.send(KafkaTopics.PRODUCT_CREATED, productId.toString(), payload).get();

        // Then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(hashtagJpaRepository.findByNameAndDeletedAtIsNull(normalizedHashtagName)).isPresent());

        Hashtag hashtag = hashtagJpaRepository.findByNameAndDeletedAtIsNull(normalizedHashtagName).orElseThrow();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Hashtag reloaded = hashtagJpaRepository.findById(hashtag.getId()).orElseThrow();
            assertThat(reloaded.getUsageCount()).isEqualTo(1L);

            List<HashtagProduct> links = hashtagProductJpaRepository.findAll().stream()
                    .filter(link -> link.getHashtag().getId().equals(hashtag.getId())
                            && link.getProductId().equals(productId))
                    .toList();
            assertThat(links).hasSize(1);

            List<OutboxEvent> outboxEvents = outboxEventJpaRepository.findAll().stream()
                    .filter(outboxEvent -> outboxEvent.getType() == OutboxEventType.HASHTAG_CREATED
                            && outboxEvent.getPayload().contains(hashtag.getId().toString()))
                    .toList();
            assertThat(outboxEvents).hasSize(1);
        });
    }
}
