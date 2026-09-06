package com.sub9.orderservice.payment.domain.model;

import com.sub9.orderservice.common.entity.BaseEntity;
import com.sub9.orderservice.order.domain.model.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Getter
public class PaymentCancellation extends BaseEntity {

    private final UUID paymentId;
    private final UUID commandRequestId;
    private final Money amount;
    private final String reasonCode = "CUSTOMER_REQUEST";
    private final Instant canceledAt;

    PaymentCancellation(UUID id, UUID paymentId, UUID commandRequestId, Money amount, Instant canceledAt) {
        super(id);
        this.paymentId = Objects.requireNonNull(paymentId, "결제 식별자는 필수입니다.");
        this.commandRequestId = Objects.requireNonNull(commandRequestId, "취소 명령 식별자는 필수입니다.");
        this.amount = Objects.requireNonNull(amount, "취소 금액은 필수입니다.");
        this.canceledAt = Objects.requireNonNull(canceledAt, "결제 취소 시각은 필수입니다.");
    }
}
