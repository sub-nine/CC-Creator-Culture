package com.sub9.orderservice.payment.domain.model;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.common.entity.BaseEntity;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.payment.domain.exception.PaymentErrorCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "p_payments", schema = "public",
        uniqueConstraints = @UniqueConstraint(name = "uk_payments_order_id", columnNames = "order_id"),
        check = {
                @CheckConstraint(name = "ck_payments_method", constraint = "method = 'MOCK'"),
                @CheckConstraint(name = "ck_payments_amount", constraint = "amount >= 0"),
                @CheckConstraint(name = "ck_payments_status", constraint = "status in ('SUCCESS', 'FAILED')"),
                @CheckConstraint(name = "ck_payments_failure_code",
                        constraint = "(status = 'SUCCESS' and failure_code is null)"
                                + " or (status = 'FAILED' and failure_code is not null and failure_code = 'MOCK_PAYMENT_FAILED')")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    // 주문 ID만으로 결제를 생성할 수 있도록 외래 키 매핑용 연관관계를 따로 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payments_order"))
    @Getter(AccessLevel.NONE)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false, length = 20)
    private PaymentMethod method;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false, updatable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "failure_code", updatable = false, length = 50)
    private String failureCode;

    @Column(name = "processed_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant processedAt;

    @OneToOne(mappedBy = "payment", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
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
        cancellation = new PaymentCancellation(cancellationId, this, commandRequestId, amount, canceledAt);
        return cancellation;
    }
}
