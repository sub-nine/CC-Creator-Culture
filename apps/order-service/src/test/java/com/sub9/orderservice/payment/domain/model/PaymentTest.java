package com.sub9.orderservice.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("모의 결제 생성")
class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @ParameterizedTest
    @CsvSource({
            "SUCCESS, 34200,",
            "FAILED, 34200, MOCK_PAYMENT_FAILED",
            "SUCCESS, 0,",
            "FAILED, 0, MOCK_PAYMENT_FAILED"
    })
    @DisplayName("성공과 실패 결제는 0원을 허용하고 결과에 맞는 실패 코드를 보존한다")
    void when_valid_result_is_given_payment_preserves_result(
            PaymentStatus status, long amount, String failureCode) {
        UUID id = uuidGenerator.generate();
        UUID orderId = uuidGenerator.generate();

        Payment payment = Payment.create(id, orderId, Money.won(amount), status, NOW);

        assertThat(payment.getId()).isEqualTo(id);
        assertThat(payment.getOrderId()).isEqualTo(orderId);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.MOCK);
        assertThat(payment.getAmount()).isEqualTo(Money.won(amount));
        assertThat(payment.getStatus()).isEqualTo(status);
        assertThat(payment.getFailureCode()).isEqualTo(failureCode);
        assertThat(payment.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("음수 결제액은 기존 Money 검증으로 거부한다")
    void when_amount_is_negative_payment_creation_is_rejected() {
        assertThatThrownBy(() -> Payment.create(uuidGenerator.generate(), uuidGenerator.generate(),
                Money.won(-1), PaymentStatus.SUCCESS, NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(OrderErrorCode.INVALID_ORDER_AMOUNT));
    }

    @Test
    @DisplayName("UUID v7이 아닌 결제 식별자를 거부한다")
    void when_payment_id_is_not_uuid_v7_creation_is_rejected() {
        assertThatThrownBy(() -> Payment.create(UUID.randomUUID(), uuidGenerator.generate(),
                Money.won(1_000), PaymentStatus.SUCCESS, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("식별자는 UUID v7 형식이어야 합니다.");
    }

    @ParameterizedTest
    @CsvSource({
            "id, 식별자는 필수입니다.",
            "orderId, 주문 식별자는 필수입니다.",
            "amount, 결제 금액은 필수입니다.",
            "status, 결제 결과는 필수입니다.",
            "processedAt, 결제 처리 시각은 필수입니다."
    })
    @DisplayName("결제 필수값이 없으면 생성을 거부한다")
    void when_required_value_is_missing_creation_is_rejected(String field, String message) {
        assertThatThrownBy(() -> Payment.create(
                field.equals("id") ? null : uuidGenerator.generate(),
                field.equals("orderId") ? null : uuidGenerator.generate(),
                field.equals("amount") ? null : Money.won(1_000),
                field.equals("status") ? null : PaymentStatus.SUCCESS,
                field.equals("processedAt") ? null : NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(message);
    }
}
