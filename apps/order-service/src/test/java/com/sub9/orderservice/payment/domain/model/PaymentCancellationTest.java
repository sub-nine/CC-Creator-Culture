package com.sub9.orderservice.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.payment.domain.exception.PaymentErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

@DisplayName("결제 전체 취소")
class PaymentCancellationTest {

    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
    private static final Instant CANCELED_AT = NOW.plusSeconds(60);
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @ParameterizedTest
    @ValueSource(longs = {34_200, 0})
    @DisplayName("성공 결제를 원래 금액으로 전체 취소하고 결제 결과를 보존한다")
    void when_successful_payment_is_canceled_full_amount_and_original_result_are_preserved(long amount) {
        Payment payment = payment(PaymentStatus.SUCCESS, amount);
        UUID cancellationId = uuidGenerator.generate();
        UUID commandRequestId = uuidGenerator.generate();
        assertThat(payment.getCancellation()).isNull();

        PaymentCancellation cancellation = payment.cancel(cancellationId, commandRequestId, CANCELED_AT);

        assertThat(payment.getCancellation()).isSameAs(cancellation);
        assertThat(cancellation.getId()).isEqualTo(cancellationId);
        assertThat(cancellation.getPaymentId()).isEqualTo(payment.getId());
        assertThat(cancellation.getCommandRequestId()).isEqualTo(commandRequestId);
        assertThat(cancellation.getAmount()).isEqualTo(Money.won(amount));
        assertThat(cancellation.getReasonCode()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(cancellation.getCanceledAt()).isEqualTo(CANCELED_AT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getAmount()).isEqualTo(Money.won(amount));
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.MOCK);
        assertThat(payment.getFailureCode()).isNull();
        assertThat(payment.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("실패 결제의 취소를 거부하고 실패 결과를 보존한다")
    void when_failed_payment_is_canceled_cancellation_is_rejected() {
        Payment payment = payment(PaymentStatus.FAILED, 34_200);

        assertCancellationRejected(() -> payment.cancel(uuidGenerator.generate(), uuidGenerator.generate(), CANCELED_AT));

        assertThat(payment.getCancellation()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("MOCK_PAYMENT_FAILED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("같거나 다른 명령으로 재취소하면 거부하고 최초 취소 기록을 보존한다")
    void when_cancellation_is_repeated_original_cancellation_is_preserved(boolean sameCommand) {
        Payment payment = payment(PaymentStatus.SUCCESS, 34_200);
        UUID commandRequestId = uuidGenerator.generate();
        PaymentCancellation original = payment.cancel(uuidGenerator.generate(), commandRequestId, CANCELED_AT);

        assertCancellationRejected(() -> payment.cancel(uuidGenerator.generate(),
                sameCommand ? commandRequestId : uuidGenerator.generate(), CANCELED_AT.plusSeconds(60)));

        assertThat(payment.getCancellation()).isSameAs(original);
        assertThat(original.getCommandRequestId()).isEqualTo(commandRequestId);
        assertThat(original.getCanceledAt()).isEqualTo(CANCELED_AT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("UUID v7이 아닌 취소 식별자를 거부하고 결제를 취소하지 않는다")
    void when_cancellation_id_is_not_uuid_v7_payment_remains_uncanceled() {
        Payment payment = payment(PaymentStatus.SUCCESS, 34_200);

        assertThatThrownBy(() -> payment.cancel(UUID.randomUUID(), uuidGenerator.generate(), CANCELED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("식별자는 UUID v7 형식이어야 합니다.");
        assertThat(payment.getCancellation()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "id, 식별자는 필수입니다.",
            "commandRequestId, 취소 명령 식별자는 필수입니다.",
            "canceledAt, 결제 취소 시각은 필수입니다."
    })
    @DisplayName("취소 필수값 누락 시 결제를 보존하고 이후 정상 취소를 허용한다")
    void when_required_value_is_missing_payment_can_still_be_canceled(String field, String message) {
        Payment payment = payment(PaymentStatus.SUCCESS, 34_200);

        assertThatThrownBy(() -> payment.cancel(
                field.equals("id") ? null : uuidGenerator.generate(),
                field.equals("commandRequestId") ? null : uuidGenerator.generate(),
                field.equals("canceledAt") ? null : CANCELED_AT))
                .isInstanceOf(NullPointerException.class).hasMessage(message);
        assertThat(payment.getCancellation()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.cancel(uuidGenerator.generate(), uuidGenerator.generate(), CANCELED_AT))
                .isSameAs(payment.getCancellation());
    }

    private Payment payment(PaymentStatus status, long amount) {
        return Payment.create(uuidGenerator.generate(), uuidGenerator.generate(), Money.won(amount), status, NOW);
    }

    private static void assertCancellationRejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.INVALID_PAYMENT_CANCELLATION);
            assertThat(exception.getErrorCode().code()).isEqualTo("PAYMENT_0002");
            assertThat(exception.getErrorCode().status()).isEqualTo(HttpStatus.CONFLICT);
        });
    }
}
