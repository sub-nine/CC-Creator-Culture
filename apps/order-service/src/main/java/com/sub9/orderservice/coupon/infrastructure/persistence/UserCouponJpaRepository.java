package com.sub9.orderservice.coupon.infrastructure.persistence;

import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, UUID> {

    @EntityGraph(attributePaths = "coupon")
    Page<UserCoupon> findAllByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "coupon")
    Page<UserCoupon> findAllByUserIdAndStatus(
            UUID userId, UserCouponStatus status, Pageable pageable);

    boolean existsByCoupon_IdAndUserId(UUID couponId, UUID userId);
}
