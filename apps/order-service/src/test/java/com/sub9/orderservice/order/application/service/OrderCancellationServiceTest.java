package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sub9.common.dto.response.ErrorResponse;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.exception.ErrorCode;
import com.sub9.orderservice.order.application.port.output.StockOperationUncertainException;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.OrderCommandType;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("전체 주문 취소 요청 처리")
class OrderCancellationServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID COMMAND_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final OrderNumber ORDER_NUMBER = OrderNumber.issue(ORDER_ID);
    private static final String KEY = "cancel-order";

    @Mock
    private OrderCommandIdempotencyService idempotencyService;

    @Mock
    private OrderCancellationTransactionService transactionService;

    @Mock
    private StockPort stockPort;

    @InjectMocks
    private OrderCancellationService service;

    @Test
    @DisplayName("저장된 결과가 있으면 취소와 재고 복구를 실행하지 않는다")
    void when_result_exists_replay_does_not_repeat_collaboration() {
        var response = JsonMapper.builder().build().createObjectNode().put("message", "주문 취소 성공");
        when(idempotencyService.acquire(CUSTOMER_ID, OrderCommandType.CANCEL_ORDER, KEY,
                Map.of("orderNumber", ORDER_NUMBER.toString())))
                .thenReturn(new OrderCommandAcquireResult.Replay(200, response));

        OrderCancellationResult result = service.cancel(CUSTOMER_ID, KEY, ORDER_NUMBER);

        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.responseBody()).isSameAs(response);
        verifyNoInteractions(transactionService, stockPort);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("취소 트랜잭션 실패 결과를 저장하고 재고 복구를 호출하지 않는다")
    void when_transaction_fails_failure_is_saved_without_stock_restore(boolean businessFailure) {
        startCommand();
        ErrorCode errorCode = businessFailure
                ? OrderErrorCode.ORDER_ACCESS_DENIED : CommonErrorCode.INTERNAL_SERVER_ERROR;
        RuntimeException failure = businessFailure
                ? new BusinessException(errorCode) : new IllegalStateException("저장 실패");
        when(transactionService.cancel(CUSTOMER_ID, COMMAND_ID, ORDER_NUMBER)).thenThrow(failure);

        assertThatThrownBy(() -> service.cancel(CUSTOMER_ID, KEY, ORDER_NUMBER)).isSameAs(failure);

        ArgumentCaptor<ErrorResponse> response = ArgumentCaptor.forClass(ErrorResponse.class);
        verify(idempotencyService).completeFailure(eq(COMMAND_ID), eq(null),
                eq(errorCode.status().value()), response.capture());
        assertThat(response.getValue().getErrorCode()).isEqualTo(errorCode.code());
        verifyNoInteractions(stockPort);
    }

    @Test
    @DisplayName("재고 복구 결과가 불명확해도 확정된 취소 성공을 반환한다")
    void when_stock_restore_is_uncertain_cancellation_stays_successful() {
        startCommand();
        OrderCancellationResult success = new OrderCancellationResult(200, Map.of("message", "주문 취소 성공"));
        StockRestoreCommand stock = new StockRestoreCommand(ORDER_ID,
                List.of(new StockPort.StockItem(UUID.randomUUID(), 2)), StockPort.RestoreReason.ORDER_CANCEL);
        when(transactionService.cancel(CUSTOMER_ID, COMMAND_ID, ORDER_NUMBER))
                .thenReturn(new OrderCancellationTransactionService.CanceledOrder(success, stock));
        doThrow(new StockOperationUncertainException("복구 응답 유실"))
                .when(stockPort).restore(any(), anyList(), any());

        assertThat(service.cancel(CUSTOMER_ID, KEY, ORDER_NUMBER)).isSameAs(success);

        verify(stockPort).restore(ORDER_ID, stock.items(), StockPort.RestoreReason.ORDER_CANCEL);
        verify(idempotencyService, never()).completeFailure(any(), any(), anyInt(), any());
    }

    private void startCommand() {
        when(idempotencyService.acquire(CUSTOMER_ID, OrderCommandType.CANCEL_ORDER, KEY,
                Map.of("orderNumber", ORDER_NUMBER.toString())))
                .thenReturn(new OrderCommandAcquireResult.Started(COMMAND_ID));
    }
}
