package com.sub9.productservice.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub9.common.kafka.event.ProductCreatedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.infrastructure.messaging.scheduler.OutboxRelay;
import com.sub9.productservice.support.AbstractKafkaIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Category 이벤트 파이프라인 - 통합 테스트 (ProductCreatedEvent 발행 -> 해시태그/카테고리 생성 -> 조회)")
class CategoryEventPipelineIntegrationTest extends AbstractKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KafkaListenerEndpointRegistry listenerEndpointRegistry;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private OutboxRelay outboxRelay;

    @BeforeEach
    void waitForPartitionAssignment() {
        ContainerTestUtils.waitForAssignment(
                findContainerForTopic(KafkaTopics.PRODUCT_CREATED), embeddedKafka.getPartitionsPerTopic());
    }

    @Test
    @DisplayName("상품 등록 이벤트가 발행되면 해시태그가 신규 카테고리로 승격되고, 조회 API에서도 확인된다")
    void productCreatedEvent_flowsThroughToQueryableHashtagAndCategory() throws Exception {
        // Given
        UUID productId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();
        String rawHashtagName = "신상품태그" + suffix;
        String normalizedHashtagName = rawHashtagName.toUpperCase(Locale.ROOT);

        ProductCreatedEvent event = new ProductCreatedEvent(
                productId, UUID.randomUUID(), "상품" + suffix, "상품 설명", List.of(rawHashtagName));
        String payload = jsonMapper.writeValueAsString(event);

        // When - 실제 Kafka로 ProductCreatedEvent 발행(ProductCreatedKafkaPublisher가 하는 것과 동일한 방식)
        kafkaTemplate.send(KafkaTopics.PRODUCT_CREATED, productId.toString(), payload).get();

        // Then - 아래 전체 파이프라인이 실제로 다 거쳐야 조회 API에서 보인다:
        // ProductCreatedEventConsumer 소비 -> 해시태그 생성/링크 -> 아웃박스 기록
        // -> OutboxRelay 발행 -> HashtagCreatedEventConsumer 소비 -> tryLink() -> 카테고리 승격
        // test 프로파일에서는 @EnableScheduling이 꺼져있어(SchedulerConfig의 @Profile("!test")) OutboxRelay가
        // 자동으로 돌지 않으므로, 실제로 아웃박스 행이 생길 때까지 폴링하며 매번 직접 호출해준다
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            outboxRelay.publishPending();

            mockMvc.perform(get("/api/v1/hashtags")
                            .param("keyword", normalizedHashtagName)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].name").value(normalizedHashtagName))
                    // 상품 링크(+1)와 카테고리 승격(+1)에서 각각 usage_count가 증가해 총 2가 된다
                    .andExpect(jsonPath("$.data.content[0].usageCount").value(2));
        });

        // 해시태그와 동일한 이름으로 승격된 신규 카테고리도 검색 API에서 조회돼야 한다
        String categorySearchResponse = mockMvc.perform(get("/api/v1/categories")
                        .param("keyword", normalizedHashtagName)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value(normalizedHashtagName))
                .andReturn().getResponse().getContentAsString();

        UUID categoryId = UUID.fromString(
                objectMapper.readTree(categorySearchResponse).get("data").get("content").get(0).get("id").asText());

        // 그 카테고리 상세 조회에도 우리 해시태그가 연결돼 있어야 한다
        mockMvc.perform(get("/api/v1/categories/{categoryId}", categoryId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hashtags[0].name").value(normalizedHashtagName));
    }

    private MessageListenerContainer findContainerForTopic(String topic) {
        return listenerEndpointRegistry.getListenerContainers().stream()
                .filter(container -> Arrays.asList(container.getContainerProperties().getTopics()).contains(topic))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("리스너 컨테이너를 찾을 수 없음 - topic: " + topic));
    }
}
