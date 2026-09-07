package com.sub9.orderservice.coupon.domain.repository;

import com.sub9.orderservice.coupon.domain.model.Coupon;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findActiveById(UUID couponId);

    Page<Coupon> findAllActive(Pageable pageable);

    int increaseIssuedQuantityIfIssuable(UUID couponId, UUID userId, Instant issuedAt);
}
