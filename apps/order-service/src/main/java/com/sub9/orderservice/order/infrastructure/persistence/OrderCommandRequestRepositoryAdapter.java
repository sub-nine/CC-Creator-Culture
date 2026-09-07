package com.sub9.orderservice.order.infrastructure.persistence;

import com.sub9.orderservice.order.domain.model.OrderCommandRequest;
import com.sub9.orderservice.order.domain.model.OrderCommandType;
import com.sub9.orderservice.order.domain.repository.OrderCommandRequestRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderCommandRequestRepositoryAdapter implements OrderCommandRequestRepository {

    private final OrderCommandRequestJpaRepository jpaRepository;

    @Override
    public boolean insertProcessing(OrderCommandRequest request, Instant startedAt) {
        return jpaRepository.insertProcessing(
                request.getId(),
                request.getActorId(),
                request.getCommandType().name(),
                request.getIdempotencyKey(),
                request.getRequestHash(),
                LocalDateTime.ofInstant(startedAt, ZoneOffset.UTC)) == 1;
    }

    @Override
    public Optional<OrderCommandRequest> findByCommandKey(
            UUID actorId, OrderCommandType commandType, String idempotencyKey) {
        return jpaRepository.findByActorIdAndCommandTypeAndIdempotencyKey(
                actorId, commandType, idempotencyKey);
    }

    @Override
    public Optional<OrderCommandRequest> findByIdForUpdate(UUID commandRequestId) {
        return jpaRepository.findByIdForUpdate(commandRequestId);
    }

    @Override
    public OrderCommandRequest save(OrderCommandRequest request) {
        return jpaRepository.save(request);
    }
}
