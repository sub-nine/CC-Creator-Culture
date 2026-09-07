package com.sub9.orderservice.order.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.IdempotencyKey;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderCommandRequest;
import com.sub9.orderservice.order.domain.model.OrderCommandType;
import com.sub9.orderservice.order.domain.repository.OrderCommandRequestRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCommandIdempotencyService {

    private final OrderCommandRequestRepository commandRequestRepository;
    private final OrderCommandJsonCodec jsonCodec;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderCommandAcquireResult acquire(UUID actorId, OrderCommandType commandType,
            String rawIdempotencyKey, Object hashMaterial) {
        IdempotencyKey idempotencyKey = IdempotencyKey.from(rawIdempotencyKey);
        String requestHash = jsonCodec.hash(actorId, commandType, hashMaterial);
        OrderCommandRequest candidate = OrderCommandRequest.start(
                uuidGenerator.generate(), actorId, commandType, idempotencyKey, requestHash);

        if (commandRequestRepository.insertProcessing(candidate, clock.instant())) {
            return new OrderCommandAcquireResult.Started(candidate.getId());
        }

        OrderCommandRequest existing = commandRequestRepository.findByCommandKey(
                        actorId, commandType, idempotencyKey.value())
                .orElseThrow(() -> new IllegalStateException("중복된 주문 명령 기록을 찾을 수 없습니다."));
        if (!existing.hasSameRequestHash(requestHash)) {
            throw new BusinessException(OrderErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (existing.isProcessing()) {
            throw new BusinessException(OrderErrorCode.ORDER_REQUEST_IN_PROGRESS);
        }
        return new OrderCommandAcquireResult.Replay(
                existing.getResponseStatus(),
                jsonCodec.decodeResponse(existing.getResponsePayload()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeSuccess(UUID commandRequestId, Order order, int httpStatus,
            Object responseBody) {
        OrderCommandRequest request = findForUpdate(commandRequestId);
        request.completeSuccess(order, httpStatus, jsonCodec.encodeResponse(responseBody), clock.instant());
        commandRequestRepository.save(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeFailure(UUID commandRequestId, Order order, int httpStatus,
            Object responseBody) {
        OrderCommandRequest request = findForUpdate(commandRequestId);
        request.completeFailure(order, httpStatus, jsonCodec.encodeResponse(responseBody), clock.instant());
        commandRequestRepository.save(request);
    }

    private OrderCommandRequest findForUpdate(UUID commandRequestId) {
        return commandRequestRepository.findByIdForUpdate(commandRequestId)
                .orElseThrow(() -> new IllegalArgumentException("주문 명령 기록을 찾을 수 없습니다."));
    }
}
