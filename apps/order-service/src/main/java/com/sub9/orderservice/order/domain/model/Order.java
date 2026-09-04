package com.sub9.orderservice.order.domain.model;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.common.entity.BaseEntity;
import com.sub9.orderservice.common.persistence.InstantTimestampConverter;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "p_orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_orders_order_number", columnNames = "order_number"),
        indexes = {
                @Index(name = "idx_orders_user_created_at", columnList = "user_id, created_at DESC"),
                @Index(name = "idx_orders_status_expires_at", columnList = "status, expires_at")
        },
        check = {
                @CheckConstraint(name = "ck_orders_status", constraint = "status in ('PENDING_PAYMENT', 'PAID', 'PROCESSING', 'COMPLETED', 'EXPIRED', 'FAILED', 'CANCELED')"),
                @CheckConstraint(name = "ck_orders_original_amount", constraint = "original_amount >= 0"),
                @CheckConstraint(name = "ck_orders_discount_amount", constraint = "discount_amount >= 0 and discount_amount <= original_amount"),
                @CheckConstraint(name = "ck_orders_payment_amount", constraint = "payment_amount = original_amount - discount_amount"),
                @CheckConstraint(name = "ck_orders_paid_at", constraint = "(status in ('PENDING_PAYMENT', 'FAILED', 'EXPIRED') and paid_at is null) or (status in ('PAID', 'PROCESSING', 'COMPLETED', 'CANCELED') and paid_at is not null)"),
                @CheckConstraint(name = "ck_orders_canceled_at", constraint = "(status = 'CANCELED' and canceled_at is not null) or (status <> 'CANCELED' and canceled_at is null)")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(10);

    @Embedded
    private OrderNumber orderNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Getter(AccessLevel.NONE)
    private List<OrderItem> items = new ArrayList<>();

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "original_amount", nullable = false, updatable = false))
    private Money originalAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "discount_amount", nullable = false, updatable = false))
    private Money discountAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "payment_amount", nullable = false, updatable = false))
    private Money paymentAmount;

    @Embedded
    private ShippingAddress shippingAddress;

    @Convert(converter = InstantTimestampConverter.class)
    @Column(name = "expires_at", nullable = false, updatable = false, columnDefinition = "timestamp")
    private Instant expiresAt;

    @Convert(converter = InstantTimestampConverter.class)
    @Column(name = "paid_at", columnDefinition = "timestamp")
    private Instant paidAt;

    @Convert(converter = InstantTimestampConverter.class)
    @Column(name = "canceled_at", columnDefinition = "timestamp")
    private Instant canceledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private Order(UUID id, UUID customerId, ShippingAddress shippingAddress,
            List<OrderItem> items, Instant createdAt) {
        super(id);
        this.orderNumber = OrderNumber.issue(id);
        this.customerId = Objects.requireNonNull(customerId, "소비자 식별자는 필수입니다.");
        this.shippingAddress = Objects.requireNonNull(shippingAddress, "배송지는 필수입니다.");
        this.status = OrderStatus.PENDING_PAYMENT;
        this.expiresAt = Objects.requireNonNull(createdAt, "주문 생성 시각은 필수입니다.").plus(PAYMENT_WINDOW);
        addItems(items);
        calculateTotals();
    }

    public static Order create(UUID id, UUID customerId, ShippingAddress shippingAddress,
            List<OrderItem> items, Instant createdAt) {
        return new Order(id, customerId, shippingAddress, items, createdAt);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public OrderItem changeItemStatus(
            UUID creatorId, UUID orderItemId, OrderItemStatus targetStatus) {
        OrderItem item = items.stream()
                .filter(candidate -> candidate.getId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!item.getCreatorId().equals(creatorId)) {
            throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
        if (targetStatus == null || !targetStatus.isCreatorTarget()) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEM_STATUS_TRANSITION);
        }
        if (item.getStatus() == targetStatus) {
            return item;
        }
        if (status != OrderStatus.PAID && status != OrderStatus.PROCESSING) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS);
        }

        item.changeStatusTo(targetStatus);
        status = items.stream().allMatch(candidate -> candidate.getStatus() == OrderItemStatus.COMPLETED)
                ? OrderStatus.COMPLETED
                : OrderStatus.PROCESSING;
        return item;
    }

    private void addItems(List<OrderItem> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }

        Set<UUID> skuIds = new HashSet<>();
        for (OrderItem item : candidates) {
            if (item == null || item.hasOrder() || !skuIds.add(item.getSkuId())) {
                throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
            }
        }
        for (OrderItem item : candidates) {
            item.attachTo(this);
            items.add(item);
        }
    }

    private void calculateTotals() {
        Money original = Money.won(0);
        Money discount = Money.won(0);
        Money payment = Money.won(0);
        for (OrderItem item : items) {
            original = original.add(item.getOriginalAmount());
            discount = discount.add(item.getDiscountAmount());
            payment = payment.add(item.getPaymentAmount());
        }
        if (!payment.equals(original.subtract(discount))) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        originalAmount = original;
        discountAmount = discount;
        paymentAmount = payment;
    }

}
