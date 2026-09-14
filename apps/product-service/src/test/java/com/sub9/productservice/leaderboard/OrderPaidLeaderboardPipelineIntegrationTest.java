package com.sub9.productservice.leaderboard;

import com.sub9.common.kafka.event.OrderPaidEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.entity.HashtagProduct;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryHashtagJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagProductJpaRepository;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.infrastructure.redis.LeaderboardRedisKey;
import com.sub9.productservice.support.AbstractKafkaIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("리더보드 이벤트 파이프라인 - 통합 테스트 (OrderPaidEvent 발행 -> 소비 -> Redis 반영 -> 리더보드 조회)")
class OrderPaidLeaderboardPipelineIntegrationTest extends AbstractKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private HashtagJpaRepository hashtagJpaRepository;

    @Autowired
    private CategoryHashtagJpaRepository categoryHashtagJpaRepository;

    @Autowired
    private HashtagProductJpaRepository hashtagProductJpaRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerEndpointRegistry;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @BeforeEach
    void setUp() {
        cleanUpRedis();
        ContainerTestUtils.waitForAssignment(
                findContainerForTopic(KafkaTopics.ORDER_PAID), embeddedKafka.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        cleanUpRedis();
    }

    private void cleanUpRedis() {
        stringRedisTemplate.delete(LeaderboardRedisKey.current(LeaderboardType.CATEGORY));
        stringRedisTemplate.delete(LeaderboardRedisKey.current(LeaderboardType.HASHTAG));
    }

    @Test
    @DisplayName("주문 결제 이벤트가 발행되면 소비 후 Redis에 반영되고, 리더보드 조회 API에서도 확인된다")
    void orderPaidEvent_flowsThroughToQueryableLeaderboard() throws Exception {
        // Given - 상품이 속한 카테고리/해시태그 연결 준비
        UUID productId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();
        Category category = categoryJpaRepository.save(Category.create("패션" + suffix, null));
        Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿" + suffix));
        categoryHashtagJpaRepository.save(CategoryHashtag.create(
                category, hashtag, CategoryHashtagMatchType.MANUAL, CategoryHashtagStatus.MERGED, 0.0));
        hashtagProductJpaRepository.save(HashtagProduct.create(hashtag, productId));

        UUID orderId = UUID.randomUUID();
        OrderPaidEvent event = new OrderPaidEvent(
                orderId, List.of(new OrderPaidEvent.ProductQuantity(productId, 4L)));
        String payload = jsonMapper.writeValueAsString(event);

        // When - 실제 Kafka로 OrderPaidEvent 발행(OrderPaidKafkaPublisher가 하는 것과 동일한 방식)
        kafkaTemplate.send(KafkaTopics.ORDER_PAID, orderId.toString(), payload).get();

        // Then - OrderPaidEventConsumer 소비 -> LeaderboardScoreService가 Redis에 점수 반영 ->
        // LeaderboardController 조회 API에서 실제로 보이는지까지 확인한다(ORDER_PAID 가중치 1.5 * 수량 4 = 6.0)
        // Redis는 각 테스트 전후로 비워두므로 이 카테고리/해시태그가 유일한 랭킹 항목이다
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/leaderboards/categories")
                                .param("period", "DAILY")
                                .param("limit", "10")
                                .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.items.length()").value(1))
                        .andExpect(jsonPath("$.data.items[0].targetId").value(category.getId().toString()))
                        .andExpect(jsonPath("$.data.items[0].name").value("패션" + suffix))
                        .andExpect(jsonPath("$.data.items[0].score").value(6.0)));

        mockMvc.perform(get("/api/v1/leaderboards/hashtags")
                        .param("period", "DAILY")
                        .param("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].targetId").value(hashtag.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].name").value("스트릿" + suffix))
                .andExpect(jsonPath("$.data.items[0].score").value(6.0));
    }

    private MessageListenerContainer findContainerForTopic(String topic) {
        return listenerEndpointRegistry.getListenerContainers().stream()
                .filter(container -> Arrays.asList(container.getContainerProperties().getTopics()).contains(topic))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("리스너 컨테이너를 찾을 수 없음 - topic: " + topic));
    }
}
