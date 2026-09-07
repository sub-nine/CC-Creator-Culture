package com.sub9.orderservice.coupon.infrastructure.persistence;

import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserCouponRepositoryAdapter implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public Optional<UserCoupon> findById(UUID userCouponId) {
        return userCouponJpaRepository.findById(userCouponId);
    }

    @Override
    public Page<UserCoupon> findAllByUserId(
            UUID userId, UserCouponStatus status, Pageable pageable) {
        if (status == null) {
            return userCouponJpaRepository.findAllByUserId(userId, pageable);
        }
        return userCouponJpaRepository.findAllByUserIdAndStatus(userId, status, pageable);
    }

    @Override
    public boolean existsByCouponIdAndUserId(UUID couponId, UUID userId) {
        return userCouponJpaRepository.existsByCoupon_IdAndUserId(couponId, userId);
    }
}
