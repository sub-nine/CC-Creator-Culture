package com.sub9.orderservice.coupon.infrastructure.persistence;

import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryAdapter implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> findActiveById(UUID couponId) {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(couponId);
    }

    @Override
    public Page<Coupon> findAllActive(Pageable pageable) {
        return couponJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public int increaseIssuedQuantityIfIssuable(UUID couponId, UUID userId, Instant issuedAt) {
        return couponJpaRepository.increaseIssuedQuantityIfIssuable(couponId, userId, issuedAt);
    }
}
