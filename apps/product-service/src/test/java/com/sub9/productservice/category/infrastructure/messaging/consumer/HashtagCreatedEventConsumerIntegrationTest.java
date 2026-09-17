package com.sub9.productservice.category.infrastructure.messaging.consumer;

import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.application.command.port.out.EmbeddingClient;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.common.kafka.event.HashtagCreatedEvent;
import com.sub9.productservice.category.domain.model.CategoryStatus;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.CategoryVector;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryVectorJpaRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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

    @Autowired
    private CategoryVectorJpaRepository categoryVectorJpaRepository;

    @MockitoBean
    private EmbeddingClient embeddingClient;

    private static final int DIMENSION = 768;

    @BeforeEach
    void waitForPartitionAssignment() {
        MessageListenerContainer container = findContainerForTopic(KafkaTopics.HASHTAG_CREATED);
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
    }

    // 같은 JVM에서 공유되는 Testcontainers Postgres에 다른 테스트가 남긴 카테고리가 있을 수 있음.
    // 그중 벡터가 없는 카테고리가 하나라도 있으면 Embedding 스테이지가 그 후보를 Failed로 판정해
    // 새 카테고리 승격 자체가 보류되므로, 다른 테스트 데이터는 건드리지 않고(삭제 금지)
    // 벡터가 없는 기존 카테고리에만 더미 벡터를 채워 넣어 격리한다
    @BeforeEach
    void stubEmbeddingAndBackfillCategoryVectors() {
        // 해시태그 벡터(oneHot(0))와 백필용 카테고리 벡터(oneHot(1))를 직교시켜, 둘 다 zero-vector일 때
        // 생기는 cosine_distance(0/0) 미정의 문제를 피하고 유사도가 항상 0에 가깝게(무관) 나오도록 한다
        when(embeddingClient.embed(anyString())).thenReturn(oneHot(0));

        List<UUID> categoryIdsWithoutVector = categoryJpaRepository.findAllByStatusAndDeletedAtIsNull(CategoryStatus.ACTIVE).stream()
                .map(Category::getId)
                .filter(id -> !categoryVectorJpaRepository.existsById(id))
                .toList();
        for (UUID categoryId : categoryIdsWithoutVector) {
            categoryVectorJpaRepository.save(CategoryVector.of(categoryId, oneHot(1)));
        }
    }

    private float[] oneHot(int index) {
        float[] vector = new float[DIMENSION];
        vector[index] = 1.0f;
        return vector;
    }

    private MessageListenerContainer findContainerForTopic(String topic) {
        return listenerEndpointRegistry.getListenerContainers().stream()
                .filter(container -> Arrays.asList(container.getContainerProperties().getTopics()).contains(topic))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("리스너 컨테이너를 찾을 수 없음 - topic: " + topic));
    }

    @Test
    @DisplayName("HASHTAG_CREATED 이벤트를 수신하면 매칭되는 카테고리가 없을 때 새 카테고리를 만들어 연결하고, usage_count는 증가시키지 않는다")
    void consume_noMatchingCategory_promotesHashtagToNewCategoryWithoutIncreasingUsageCount() throws Exception {
        // Given
        String hashtagName = "통합테스트해시태그" + UUID.randomUUID();
        Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create(hashtagName));

        String payload = jsonMapper.writeValueAsString(new HashtagCreatedEvent(hashtag.getId()));

        // When
        kafkaTemplate.send(KafkaTopics.HASHTAG_CREATED, hashtag.getId().toString(), payload).get();

        // Then
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<Category> categories = categoryJpaRepository.findAllByStatusAndDeletedAtIsNull(CategoryStatus.ACTIVE);
            assertThat(categories).anyMatch(category -> category.getName().equals(hashtagName));

            // usage_count는 상품에 링크될 때만 증가하므로, 카테고리 승격으로는 늘지 않는다
            Hashtag persisted = hashtagJpaRepository.findById(hashtag.getId()).orElseThrow();
            assertThat(persisted.getUsageCount()).isZero();
        });
    }
}
