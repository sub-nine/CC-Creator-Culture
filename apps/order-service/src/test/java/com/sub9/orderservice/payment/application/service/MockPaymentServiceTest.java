package com.sub9.orderservice.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.application.port.output.StockOperationUncertainException;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.payment.application.dto.MockPaymentResult;
import com.sub9.orderservice.payment.application.service.MockPaymentTransactionService.ProcessedPayment;
import com.sub9.orderservice.payment.domain.model.PaymentMethod;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("결제 커밋 이후 재고 복구")
class MockPaymentServiceTest {

    private final UuidV7Generator ids = new UuidV7Generator();
    private final UUID customerId = ids.generate();
    private final UUID orderId = ids.generate();
    private final OrderNumber orderNumber = OrderNumber.issue(orderId);

    @Mock private MockPaymentTransactionService transactions;
    @Mock private StockPort stock;
    @InjectMocks private MockPaymentService service;

    @Test
    @DisplayName("새로운 실패 결제 처리가 끝난 뒤 재고 복구를 요청한다")
    void when_new_failure_is_committed_stock_restore_is_requested() {
        var processed = new ProcessedPayment(result(PaymentStatus.FAILED), restore());
        when(transactions.process(customerId, orderNumber, PaymentStatus.FAILED)).thenReturn(processed);

        assertThat(service.process(customerId, orderNumber, PaymentStatus.FAILED)).isEqualTo(processed.result());

        var calls = inOrder(transactions, stock);
        calls.verify(transactions).process(customerId, orderNumber, PaymentStatus.FAILED);
        calls.verify(stock).restore(orderId, processed.stockRestore().items(), RestoreReason.PAYMENT_FAILED);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("성공 또는 저장된 실패 결과에는 재고 복구를 요청하지 않는다")
    void when_no_restore_command_is_returned_stock_is_not_called(PaymentStatus status) {
        var processed = new ProcessedPayment(result(status), null);
        when(transactions.process(customerId, orderNumber, status)).thenReturn(processed);

        assertThat(service.process(customerId, orderNumber, status)).isEqualTo(processed.result());
        verifyNoInteractions(stock);
    }

    @Test
    @DisplayName("로컬 결제 처리가 실패하면 재고 복구를 요청하지 않는다")
    void when_local_transaction_fails_stock_is_not_called() {
        RuntimeException failure = new IllegalStateException("결제 저장 실패");
        when(transactions.process(customerId, orderNumber, PaymentStatus.FAILED)).thenThrow(failure);

        assertThatThrownBy(() -> service.process(customerId, orderNumber, PaymentStatus.FAILED))
                .isSameAs(failure);
        verifyNoInteractions(stock);
    }

    @Test
    @DisplayName("재고 복구 응답이 유실되어도 이미 저장한 실패 결과를 반환한다")
    void when_stock_restore_is_uncertain_committed_failure_is_returned() {
        var processed = new ProcessedPayment(result(PaymentStatus.FAILED), restore());
        when(transactions.process(customerId, orderNumber, PaymentStatus.FAILED)).thenReturn(processed);
        doThrow(new StockOperationUncertainException("재고 복구 응답 유실"))
                .when(stock).restore(orderId, processed.stockRestore().items(), RestoreReason.PAYMENT_FAILED);

        assertThat(service.process(customerId, orderNumber, PaymentStatus.FAILED)).isEqualTo(processed.result());
    }

    private MockPaymentResult result(PaymentStatus status) {
        return new MockPaymentResult(ids.generate(), orderNumber.toString(), PaymentMethod.MOCK,
                status, 1000, status == PaymentStatus.FAILED ? "MOCK_PAYMENT_FAILED" : null,
                Instant.parse("2026-09-07T00:01:00Z"));
    }

    private StockRestoreCommand restore() {
        return new StockRestoreCommand(orderId, List.of(new StockItem(ids.generate(), 2)),
                RestoreReason.PAYMENT_FAILED);
    }
}
