package com.sub9.orderservice.order.domain.model;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.common.entity.BaseEntity;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "p_order_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_items_order_sku",
                columnNames = {"order_id", "sku_id"}),
        indexes = {
                @Index(name = "idx_order_items_order_id", columnList = "order_id"),
                @Index(name = "idx_order_items_creator_status_created", columnList = "creator_id, status, created_at DESC")
        },
        check = {
                @CheckConstraint(name = "ck_order_items_status", constraint = "status in ('ORDERED', 'PREPARING', 'SHIPPED', 'DELIVERED', 'COMPLETED', 'CANCELED')"),
                @CheckConstraint(name = "ck_order_items_quantity", constraint = "quantity > 0"),
                @CheckConstraint(name = "ck_order_items_unit_price", constraint = "unit_price >= 0"),
                @CheckConstraint(name = "ck_order_items_original_amount", constraint = "original_amount = unit_price * quantity"),
                @CheckConstraint(name = "ck_order_items_discount_amount", constraint = "discount_amount >= 0 and discount_amount <= original_amount"),
                @CheckConstraint(name = "ck_order_items_payment_amount", constraint = "payment_amount = original_amount - discount_amount")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_order_items_order"))
    private Order order;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private UUID creatorId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "sku_id", nullable = false, updatable = false)
    private UUID skuId;

    @Column(name = "user_coupon_id", updatable = false)
    private UUID userCouponId;

    @Embedded
    private ProductSnapshot productSnapshot;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "original_amount", nullable = false, updatable = false))
    private Money originalAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "discount_amount", nullable = false, updatable = false))
    private Money discountAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "payment_amount", nullable = false, updatable = false))
    private Money paymentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderItemStatus status;

    private OrderItem(UUID id, UUID creatorId, UUID productId, UUID skuId, UUID userCouponId,
            ProductSnapshot productSnapshot, Money discountAmount) {
        super(id);
        this.creatorId = Objects.requireNonNull(creatorId, "창작자 식별자는 필수입니다.");
        this.productId = Objects.requireNonNull(productId, "상품 식별자는 필수입니다.");
        this.skuId = Objects.requireNonNull(skuId, "SKU 식별자는 필수입니다.");
        this.userCouponId = userCouponId;
        this.productSnapshot = Objects.requireNonNull(productSnapshot, "상품 스냅샷은 필수입니다.");
        this.originalAmount = productSnapshot.originalAmount();
        this.discountAmount = Objects.requireNonNull(discountAmount, "할인 금액은 필수입니다.");
        this.paymentAmount = originalAmount.subtract(discountAmount);
        this.status = OrderItemStatus.ORDERED;
    }

    public static OrderItem create(UUID id, UUID creatorId, UUID productId, UUID skuId,
            UUID userCouponId, ProductSnapshot productSnapshot, Money discountAmount) {
        return new OrderItem(id, creatorId, productId, skuId, userCouponId, productSnapshot, discountAmount);
    }

    public UUID getOrderId() {
        return order == null ? null : order.getId();
    }

    boolean isAttached() {
        return order != null;
    }

    void attachTo(Order order) {
        Objects.requireNonNull(order, "주문은 필수입니다.");
        if (this.order != null && this.order != order) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }
        this.order = order;
    }

}
