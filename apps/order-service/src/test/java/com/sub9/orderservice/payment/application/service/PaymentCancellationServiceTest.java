package com.sub9.orderservice.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.payment.domain.exception.PaymentErrorCode;
import com.sub9.orderservice.payment.domain.model.Payment;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import com.sub9.orderservice.payment.domain.repository.PaymentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCancellationServiceTest {

    private final UuidV7Generator ids = new UuidV7Generator();
    private final UUID orderId = ids.generate();
    private final UUID commandId = ids.generate();
    private final Instant now = Instant.parse("2026-09-07T00:00:00Z");
    @Mock private PaymentRepository payments;

    @ParameterizedTest
    @ValueSource(longs = {0, 10000})
    void when_successful_payment_is_canceled_full_amount_is_saved(long amount) {
        Payment payment = Payment.create(ids.generate(), orderId, Money.won(amount), PaymentStatus.SUCCESS, now);
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        new PaymentCancellationService(payments, ids).cancel(orderId, commandId, now.plusSeconds(60));

        verify(payments).save(payment);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getCancellation().getAmount()).isEqualTo(Money.won(amount));
        assertThat(payment.getCancellation().getId().version()).isEqualTo(7);
        assertThat(payment.getCancellation().getCommandRequestId()).isEqualTo(commandId);
        assertThat(payment.getCancellation().getCanceledAt()).isEqualTo(now.plusSeconds(60));
        assertThat(payment.getCancellation().getReasonCode()).isEqualTo("CUSTOMER_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "failed", "canceled"})
    void when_payment_is_ineligible_cancellation_is_rejected(String condition) {
        Payment payment = Payment.create(ids.generate(), orderId, Money.won(10000),
                condition.equals("failed") ? PaymentStatus.FAILED : PaymentStatus.SUCCESS, now);
        if (condition.equals("canceled")) {
            payment.cancel(ids.generate(), ids.generate(), now);
        }
        when(payments.findByOrderId(orderId)).thenReturn(
                condition.equals("missing") ? Optional.empty() : Optional.of(payment));

        assertThatThrownBy(() -> new PaymentCancellationService(payments, ids).cancel(orderId, commandId, now))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.INVALID_PAYMENT_CANCELLATION));
        verify(payments, never()).save(any());
    }
}
