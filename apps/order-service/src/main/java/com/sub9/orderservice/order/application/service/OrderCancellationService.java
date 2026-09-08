package com.sub9.orderservice.order.application.service;

import com.sub9.common.dto.response.ErrorResponse;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.exception.ErrorCode;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import com.sub9.orderservice.order.application.service.OrderCancellationTransactionService.CanceledOrder;
import com.sub9.orderservice.order.domain.model.OrderCommandType;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private final OrderCommandIdempotencyService idempotencyService;
    private final OrderCancellationTransactionService transactionService;
    private final StockPort stockPort;

    // 재고 복구 전에 주문 취소 커밋이 끝나야 하므로 외부 트랜잭션 참여를 금지합니다.
    @Transactional(propagation = Propagation.NEVER)
    public OrderCancellationResult cancel(UUID customerId, String idempotencyKey, OrderNumber orderNumber) {
        OrderCommandAcquireResult acquired = idempotencyService.acquire(
                customerId, OrderCommandType.CANCEL_ORDER, idempotencyKey,
                Map.of("orderNumber", orderNumber.toString()));
        if (acquired instanceof OrderCommandAcquireResult.Replay replay) {
            return new OrderCancellationResult(replay.httpStatus(), replay.responseBody());
        }

        UUID commandRequestId = ((OrderCommandAcquireResult.Started) acquired).commandRequestId();
        CanceledOrder canceled;
        try {
            canceled = transactionService.cancel(customerId, commandRequestId, orderNumber);
        } catch (BusinessException exception) {
            completeFailure(commandRequestId, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            completeFailure(commandRequestId, CommonErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }

        StockRestoreCommand command = canceled.stockRestore();
        try {
            stockPort.restore(command.orderId(), command.items(), command.reason());
        } catch (RuntimeException exception) {
            // ponytail: 복구 실패는 로그만 기록하며 전달 보장이 필요하면 영속 재시도 작업을 추가합니다.
            log.error("주문 취소 후 재고 복구에 실패했습니다. orderId={}, reason={}",
                    command.orderId(), command.reason(), exception);
        }
        return canceled.result();
    }

    private void completeFailure(UUID commandRequestId, ErrorCode errorCode) {
        idempotencyService.completeFailure(
                commandRequestId, null, errorCode.status().value(), ErrorResponse.from(errorCode));
    }
}
