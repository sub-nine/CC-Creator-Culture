package com.sub9.orderservice.payment.domain.model;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.common.entity.BaseEntity;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.payment.domain.exception.PaymentErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Getter
public class Payment extends BaseEntity {

    private final UUID orderId;
    private final PaymentMethod method;
    private final Money amount;
    private final PaymentStatus status;
    private final String failureCode;
    private final Instant processedAt;
    private PaymentCancellation cancellation;

    private Payment(UUID id, UUID orderId, Money amount, PaymentStatus status, Instant processedAt) {
        super(id);
        this.orderId = Objects.requireNonNull(orderId, "주문 식별자는 필수입니다.");
        this.method = PaymentMethod.MOCK;
        this.amount = Objects.requireNonNull(amount, "결제 금액은 필수입니다.");
        this.status = Objects.requireNonNull(status, "결제 결과는 필수입니다.");
        this.failureCode = status == PaymentStatus.FAILED ? "MOCK_PAYMENT_FAILED" : null;
        this.processedAt = Objects.requireNonNull(processedAt, "결제 처리 시각은 필수입니다.");
    }

    public static Payment create(UUID id, UUID orderId, Money amount, PaymentStatus status,
            Instant processedAt) {
        return new Payment(id, orderId, amount, status, processedAt);
    }

    public PaymentCancellation cancel(UUID cancellationId, UUID commandRequestId, Instant canceledAt) {
        if (status != PaymentStatus.SUCCESS || cancellation != null) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_CANCELLATION);
        }
        cancellation = new PaymentCancellation(cancellationId, getId(), commandRequestId, amount, canceledAt);
        return cancellation;
    }
}
