package com.sub9.orderservice.coupon.domain.model;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "p_user_coupons",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_coupon_user_coupon",
                columnNames = {"user_id", "coupon_id"}),
        indexes = {
                @Index(name = "idx_user_coupon_user_status", columnList = "user_id, status"),
                @Index(name = "idx_user_coupon_coupon", columnList = "coupon_id")
        },
        check = {
                @CheckConstraint(name = "chk_user_coupon_status", constraint = "status in ('ISSUED', 'USED')"),
                @CheckConstraint(
                        name = "chk_user_coupon_usage",
                        constraint = "(status = 'ISSUED' and used_at is null and order_id is null) or "
                                + "(status = 'USED' and used_at is not null and order_id is not null)")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon extends CouponBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "coupon_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_coupons_coupon"))
    private Coupon coupon;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserCouponStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant issuedAt;

    @Column(name = "used_at", columnDefinition = "timestamp with time zone")
    private Instant usedAt;

    @Column(name = "order_id")
    private UUID orderId;

    private UserCoupon(UUID id, Coupon coupon, UUID userId, Instant issuedAt) {
        super(id, userId, issuedAt);
        this.coupon = Objects.requireNonNull(coupon, "쿠폰은 필수입니다.");
        this.userId = Objects.requireNonNull(userId, "사용자 식별자는 필수입니다.");
        this.status = UserCouponStatus.ISSUED;
        this.issuedAt = Objects.requireNonNull(issuedAt, "쿠폰 발급 시각은 필수입니다.");
    }

    public static UserCoupon issue(UUID id, Coupon coupon, UUID userId, Instant issuedAt) {
        return new UserCoupon(id, coupon, userId, issuedAt);
    }

    public UUID getCouponId() {
        return coupon.getId();
    }

    public void use(UUID userId, UUID orderId, Instant usedAt) {
        if (!this.userId.equals(Objects.requireNonNull(userId, "사용자 식별자는 필수입니다."))) {
            throw new IllegalArgumentException("쿠폰 소유자만 사용할 수 있습니다.");
        }
        Objects.requireNonNull(orderId, "주문 식별자는 필수입니다.");
        Objects.requireNonNull(usedAt, "쿠폰 사용 시각은 필수입니다.");
        if (status == UserCouponStatus.USED) {
            throw new IllegalStateException("이미 사용된 쿠폰입니다.");
        }
        this.status = UserCouponStatus.USED;
        this.orderId = orderId;
        this.usedAt = usedAt;
        updateAudit(userId, usedAt);
    }
}
