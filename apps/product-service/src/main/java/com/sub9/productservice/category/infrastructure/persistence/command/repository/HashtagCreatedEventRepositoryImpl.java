package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.category.application.command.port.out.HashtagCreatedEventPort;
import com.sub9.productservice.category.domain.event.HashtagCreatedEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.OutboxEventJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class HashtagCreatedEventRepositoryImpl implements HashtagCreatedEventPort {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final JsonMapper jsonMapper;

    @Override
    public void record(HashtagCreatedEvent event) {
        try {
            String payload = jsonMapper.writeValueAsString(event);
            outboxEventJpaRepository.save(OutboxEvent.pending(OutboxEventType.HASHTAG_CREATED, payload));
        } catch (JacksonException e) {
            log.error("[OUTBOX] 이벤트 직렬화 실패 - event: {}", event, e);
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
