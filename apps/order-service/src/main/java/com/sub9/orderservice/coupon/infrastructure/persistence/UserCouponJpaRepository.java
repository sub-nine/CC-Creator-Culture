package com.sub9.orderservice.coupon.infrastructure.persistence;

import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, UUID> {

    boolean existsByCoupon_IdAndUserId(UUID couponId, UUID userId);
}
