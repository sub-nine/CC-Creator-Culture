package com.sub9.productservice.category.infrastructure.messaging.consumer;

import com.sub9.common.kafka.event.CategoryCreatedEvent;
import com.sub9.productservice.category.application.command.port.in.CalculateCategoryVectorUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryCreatedEventConsumer 단위 테스트")
class CategoryCreatedEventConsumerTest {

    @Mock
    private CalculateCategoryVectorUseCase calculateCategoryVectorUseCase;
    @Mock
    private Acknowledgment acknowledgment;

    private CategoryCreatedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CategoryCreatedEventConsumer(calculateCategoryVectorUseCase);
    }

    @Test
    @DisplayName("정상 처리되면 벡터 계산 후 ack를 보낸다")
    void consume_success_acknowledges() {
        CategoryCreatedEvent event = new CategoryCreatedEvent(UUID.randomUUID());

        consumer.consume(event, acknowledgment);

        verify(calculateCategoryVectorUseCase).calculate(event.categoryId());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("벡터 계산이 실패하면 예외를 던지고 ack를 보내지 않는다 (Kafka 재전달 유도)")
    void consume_calculationFails_throwsAndDoesNotAcknowledge() {
        CategoryCreatedEvent event = new CategoryCreatedEvent(UUID.randomUUID());
        doThrow(new RuntimeException("임베딩 모델 타임아웃"))
                .when(calculateCategoryVectorUseCase).calculate(event.categoryId());

        assertThatThrownBy(() -> consumer.consume(event, acknowledgment))
                .isInstanceOf(IllegalStateException.class);

        verify(acknowledgment, never()).acknowledge();
    }
}
