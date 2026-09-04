package com.sub9.orderservice.order.domain.repository;

import com.sub9.orderservice.order.domain.model.OrderCommandRequest;
import com.sub9.orderservice.order.domain.model.OrderCommandType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderCommandRequestRepository {

    boolean insertProcessing(OrderCommandRequest request, Instant startedAt);

    Optional<OrderCommandRequest> findByCommandKey(
            UUID actorId, OrderCommandType commandType, String idempotencyKey);

    Optional<OrderCommandRequest> findByIdForUpdate(UUID commandRequestId);

    OrderCommandRequest save(OrderCommandRequest request);
}
