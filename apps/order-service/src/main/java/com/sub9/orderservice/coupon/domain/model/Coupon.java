package com.sub9.orderservice.coupon.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "p_coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends CouponBaseEntity {

    @Column(name = "coupon_name", nullable = false, length = 100)
    private String couponName;

    @Column(name = "discount_rate", nullable = false)
    private int discountRate;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @ColumnDefault("0")
    @Column(name = "issued_quantity", nullable = false)
    private int issuedQuantity;

    @Column(name = "started_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant startedAt;

    @Column(name = "expired_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant expiredAt;

    private Coupon(UUID id, String couponName, int discountRate, int totalQuantity,
            Instant startedAt, Instant expiredAt, UUID createdBy, Instant createdAt) {
        super(id, createdBy, createdAt);
        this.couponName = requireCouponName(couponName);
        this.discountRate = requireDiscountRate(discountRate);
        this.totalQuantity = requireTotalQuantity(totalQuantity);
        this.startedAt = Objects.requireNonNull(startedAt, "쿠폰 시작 시각은 필수입니다.");
        this.expiredAt = Objects.requireNonNull(expiredAt, "쿠폰 만료 시각은 필수입니다.");
        if (!startedAt.isBefore(expiredAt)) {
            throw new IllegalArgumentException("쿠폰 시작 시각은 만료 시각보다 빨라야 합니다.");
        }
        this.issuedQuantity = 0;
    }

    public static Coupon create(UUID id, String couponName, int discountRate, int totalQuantity,
            Instant startedAt, Instant expiredAt, UUID createdBy, Instant createdAt) {
        return new Coupon(id, couponName, discountRate, totalQuantity,
                startedAt, expiredAt, createdBy, createdAt);
    }

    public boolean isIssuableAt(Instant now) {
        Instant requestedAt = Objects.requireNonNull(now, "발급 요청 시각은 필수입니다.");
        return !isDeleted()
                && !requestedAt.isBefore(startedAt)
                && !requestedAt.isAfter(expiredAt)
                && issuedQuantity < totalQuantity;
    }

    public void issue(UUID userId, Instant issuedAt) {
        if (!isIssuableAt(issuedAt)) {
            throw new IllegalStateException("현재 발급할 수 없는 쿠폰입니다.");
        }
        issuedQuantity++;
        updateAudit(userId, issuedAt);
    }

    public void delete(UUID actorId, Instant deletedAt) {
        softDelete(actorId, deletedAt);
    }

    private static String requireCouponName(String couponName) {
        Objects.requireNonNull(couponName, "쿠폰 이름은 필수입니다.");
        String trimmed = couponName.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new IllegalArgumentException("쿠폰 이름은 1자 이상 100자 이하여야 합니다.");
        }
        return trimmed;
    }

    private static int requireDiscountRate(int discountRate) {
        if (discountRate < 1 || discountRate > 100) {
            throw new IllegalArgumentException("할인율은 1 이상 100 이하여야 합니다.");
        }
        return discountRate;
    }

    private static int requireTotalQuantity(int totalQuantity) {
        if (totalQuantity < 1) {
            throw new IllegalArgumentException("총 발급 수량은 1 이상이어야 합니다.");
        }
        return totalQuantity;
    }
}
