package com.sub9.orderservice.coupon.domain.repository;

import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> findById(UUID userCouponId);

    Page<UserCoupon> findAllByUserId(UUID userId, UserCouponStatus status, Pageable pageable);

    boolean existsByCouponIdAndUserId(UUID couponId, UUID userId);

    int useIfAvailable(UUID userCouponId, UUID orderId, Instant usedAt);

    int restoreIfUsedByOrder(UUID userCouponId, UUID orderId, Instant restoredAt);
}
