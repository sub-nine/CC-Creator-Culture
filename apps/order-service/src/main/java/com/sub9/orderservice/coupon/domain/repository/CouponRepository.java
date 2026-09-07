package com.sub9.orderservice.coupon.domain.repository;

import com.sub9.orderservice.coupon.domain.model.Coupon;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findActiveById(UUID couponId);

    int increaseIssuedQuantityIfIssuable(UUID couponId, UUID userId, Instant issuedAt);
}
