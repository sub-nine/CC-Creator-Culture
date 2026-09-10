package com.sub9.productservice.leaderboard.infrastructure.messaging.consumer;

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
import org.springframework.data.redis.core.StringRedisTemplate;
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
@DisplayName("OrderPaidEventConsumer - 통합 테스트")
class OrderPaidEventConsumerIntegrationTest extends AbstractKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

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
    @DisplayName("주문 결제 이벤트를 수신하면 상품이 속한 카테고리/해시태그의 실시간 점수를 증가시킨다")
    void consume_incrementsCategoryAndHashtagScores() throws Exception {
        // Given
        UUID productId = UUID.randomUUID();
        Category category = categoryJpaRepository.save(Category.create("패션" + UUID.randomUUID(), null));
        Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿" + UUID.randomUUID()));
        categoryHashtagJpaRepository.save(CategoryHashtag.create(
                category, hashtag, CategoryHashtagMatchType.MANUAL, CategoryHashtagStatus.MERGED, 0.0));
        hashtagProductJpaRepository.save(HashtagProduct.create(hashtag, productId));

        UUID orderId = UUID.randomUUID();
        OrderPaidEvent event = new OrderPaidEvent(
                orderId, List.of(new OrderPaidEvent.ProductQuantity(productId, 4L)));
        String payload = jsonMapper.writeValueAsString(event);

        // When
        kafkaTemplate.send(KafkaTopics.ORDER_PAID, orderId.toString(), payload).get();

        // Then - ORDER_PAID 가중치(1.5) * 수량(4) = 6.0
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Double categoryScore = stringRedisTemplate.opsForZSet().score(
                    LeaderboardRedisKey.current(LeaderboardType.CATEGORY), category.getId().toString());
            Double hashtagScore = stringRedisTemplate.opsForZSet().score(
                    LeaderboardRedisKey.current(LeaderboardType.HASHTAG), hashtag.getId().toString());

            assertThat(categoryScore).isEqualTo(6.0);
            assertThat(hashtagScore).isEqualTo(6.0);
        });
    }

    private MessageListenerContainer findContainerForTopic(String topic) {
        return listenerEndpointRegistry.getListenerContainers().stream()
                .filter(container -> Arrays.asList(container.getContainerProperties().getTopics()).contains(topic))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("리스너 컨테이너를 찾을 수 없음 - topic: " + topic));
    }
}
