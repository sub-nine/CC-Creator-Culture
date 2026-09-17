package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.kafka.event.CategoryCreatedEvent;
import com.sub9.common.kafka.event.HashtagCreatedEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEventType;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxStatus;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.OutboxEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRepositoryImpl 단위 테스트")
class OutboxRepositoryImplTest {

    @Mock
    private OutboxEventJpaRepository outboxEventJpaRepository;
    @Mock
    private JsonMapper jsonMapper;

    private OutboxRepositoryImpl outboxRepository;

    @BeforeEach
    void setUp() {
        outboxRepository = new OutboxRepositoryImpl(outboxEventJpaRepository, jsonMapper);
    }

    @Test
    @DisplayName("HashtagCreatedEvent를 직렬화해 HASHTAG_CREATED 타입의 PENDING 이벤트로 저장한다")
    void record_hashtagCreatedEvent_savesPendingOutboxEvent() {
        HashtagCreatedEvent event = new HashtagCreatedEvent(UUID.randomUUID());
        when(jsonMapper.writeValueAsString(event)).thenReturn("{\"hashtagId\":\"" + event.hashtagId() + "\"}");

        outboxRepository.record(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventJpaRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(OutboxEventType.HASHTAG_CREATED);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getPayload()).contains(event.hashtagId().toString());
    }

    @Test
    @DisplayName("CategoryCreatedEvent를 직렬화해 CATEGORY_CREATED 타입의 PENDING 이벤트로 저장한다")
    void record_categoryCreatedEvent_savesPendingOutboxEvent() {
        CategoryCreatedEvent event = new CategoryCreatedEvent(UUID.randomUUID());
        when(jsonMapper.writeValueAsString(event)).thenReturn("{\"categoryId\":\"" + event.categoryId() + "\"}");

        outboxRepository.record(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventJpaRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(OutboxEventType.CATEGORY_CREATED);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getPayload()).contains(event.categoryId().toString());
    }

    @Test
    @DisplayName("직렬화에 실패하면 BusinessException을 던지고 저장하지 않는다")
    void record_serializationFails_throwsBusinessException() {
        CategoryCreatedEvent event = new CategoryCreatedEvent(UUID.randomUUID());
        when(jsonMapper.writeValueAsString(event)).thenThrow(JacksonException.class);

        assertThatThrownBy(() -> outboxRepository.record(event))
                .isInstanceOf(BusinessException.class);
    }
}
