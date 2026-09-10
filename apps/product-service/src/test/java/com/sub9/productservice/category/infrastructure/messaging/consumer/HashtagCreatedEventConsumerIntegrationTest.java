package com.sub9.productservice.category.infrastructure.messaging.consumer;

import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.event.HashtagCreatedEvent;
import com.sub9.productservice.category.domain.model.CategoryStatus;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagJpaRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@DisplayName("HashtagCreatedEventConsumer - 통합 테스트")
class HashtagCreatedEventConsumerIntegrationTest extends AbstractKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private HashtagJpaRepository hashtagJpaRepository;

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private KafkaListenerEndpointRegistry listenerEndpointRegistry;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @BeforeEach
    void waitForPartitionAssignment() {
        MessageListenerContainer container = findContainerForTopic(KafkaTopics.HASHTAG_CREATED);
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
    }

    private MessageListenerContainer findContainerForTopic(String topic) {
        return listenerEndpointRegistry.getListenerContainers().stream()
                .filter(container -> Arrays.asList(container.getContainerProperties().getTopics()).contains(topic))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("리스너 컨테이너를 찾을 수 없음 - topic: " + topic));
    }

    @Test
    @DisplayName("HASHTAG_CREATED 이벤트를 수신하면 매칭되는 카테고리가 없을 때 새 카테고리를 만들어 연결한다")
    void consume_noMatchingCategory_promotesHashtagToNewCategory() throws Exception {
        // Given
        String hashtagName = "통합테스트해시태그" + UUID.randomUUID();
        Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create(hashtagName));

        String payload = jsonMapper.writeValueAsString(new HashtagCreatedEvent(hashtag.getId()));

        // When
        kafkaTemplate.send(KafkaTopics.HASHTAG_CREATED, hashtag.getId().toString(), payload).get();

        // Then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Category> categories = categoryJpaRepository.findAllByStatusAndDeletedAtIsNull(CategoryStatus.ACTIVE);
            assertThat(categories).anyMatch(category -> category.getName().equals(hashtagName));

            Hashtag persisted = hashtagJpaRepository.findById(hashtag.getId()).orElseThrow();
            assertThat(persisted.getUsageCount()).isEqualTo(1L);
        });
    }
}
