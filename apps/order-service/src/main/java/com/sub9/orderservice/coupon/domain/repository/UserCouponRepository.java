package com.sub9.orderservice.coupon.domain.repository;

import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import java.util.Optional;
import java.util.UUID;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> findById(UUID userCouponId);

    boolean existsByCouponIdAndUserId(UUID couponId, UUID userId);
}
