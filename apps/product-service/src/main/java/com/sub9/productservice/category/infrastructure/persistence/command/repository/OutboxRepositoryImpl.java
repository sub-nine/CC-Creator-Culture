package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.category.application.command.port.out.OutboxRepository;
import com.sub9.common.kafka.event.CategoryCreatedEvent;
import com.sub9.common.kafka.event.HashtagCreatedEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEventType;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final JsonMapper jsonMapper;

    @Override
    public void record(HashtagCreatedEvent event) {
        record(OutboxEventType.HASHTAG_CREATED, event);
    }

    @Override
    public void record(CategoryCreatedEvent event) {
        record(OutboxEventType.CATEGORY_CREATED, event);
    }

    private void record(OutboxEventType type, Object event) {
        try {
            String payload = jsonMapper.writeValueAsString(event);
            outboxEventJpaRepository.save(OutboxEvent.pending(type, payload));
        } catch (JacksonException e) {
            log.error("[OUTBOX] 이벤트 직렬화 실패 - event: {}", event, e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
