package com.sub9.productservice.category.infrastructure.messaging.publisher;

import com.sub9.common.kafka.event.CategoryCreatedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryCreatedEventOutboxPublisher 단위 테스트")
class CategoryCreatedEventOutboxPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private CategoryCreatedEventOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CategoryCreatedEventOutboxPublisher(kafkaTemplate, jsonMapper);
    }

    @Test
    @DisplayName("CATEGORY_CREATED 타입만 지원한다")
    void supports_onlyCategoryCreated() {
        assertThat(publisher.supports(OutboxEventType.CATEGORY_CREATED)).isTrue();
        assertThat(publisher.supports(OutboxEventType.HASHTAG_CREATED)).isFalse();
    }

    @Test
    @DisplayName("payload를 역직렬화해서 categoryId를 key로, 원본 payload를 value로 Kafka에 발행한다")
    void publish_sendsToKafkaWithCategoryIdAsKey() {
        CategoryCreatedEvent event = new CategoryCreatedEvent(UUID.randomUUID());
        String payload = jsonMapper.writeValueAsString(event);
        OutboxEvent outboxEvent = OutboxEvent.pending(OutboxEventType.CATEGORY_CREATED, payload);

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq(KafkaTopics.CATEGORY_CREATED), eq(event.categoryId().toString()), anyString()))
                .thenReturn(future);

        publisher.publish(outboxEvent).join();

        // 위 when()의 eq() 매칭이 통과했다는 것 자체가 key/payload가 올바르게 전달됐다는 검증
        assertThat(payload).contains(event.categoryId().toString());
    }
}
