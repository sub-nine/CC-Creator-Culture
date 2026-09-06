package com.sub9.orderservice.payment.domain.model;

import com.sub9.orderservice.common.entity.BaseEntity;
import com.sub9.orderservice.common.persistence.InstantTimestampConverter;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.OrderCommandRequest;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "p_payment_cancellations", schema = "public")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancellation extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_cancellations_payment"))
    @Getter(AccessLevel.NONE)
    private Payment payment;

    @Column(name = "command_request_id", nullable = false, updatable = false)
    private UUID commandRequestId;

    // 명령 ID는 값으로 보관하고 이 연관관계는 DB 외래 키 매핑에만 사용한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "command_request_id", nullable = false, insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_cancellations_command"))
    @Getter(AccessLevel.NONE)
    private OrderCommandRequest commandRequest;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false, updatable = false))
    private Money amount;

    @Column(name = "reason_code", nullable = false, updatable = false, length = 50)
    private String reasonCode = "CUSTOMER_REQUEST";

    @Convert(converter = InstantTimestampConverter.class)
    @Column(name = "canceled_at", nullable = false, updatable = false, columnDefinition = "timestamp")
    private Instant canceledAt;

    PaymentCancellation(UUID id, Payment payment, UUID commandRequestId, Money amount, Instant canceledAt) {
        super(id);
        this.payment = Objects.requireNonNull(payment, "원래 결제는 필수입니다.");
        this.commandRequestId = Objects.requireNonNull(commandRequestId, "취소 명령 식별자는 필수입니다.");
        this.amount = Objects.requireNonNull(amount, "취소 금액은 필수입니다.");
        this.canceledAt = Objects.requireNonNull(canceledAt, "결제 취소 시각은 필수입니다.");
    }

    public UUID getPaymentId() {
        return payment.getId();
    }
}
