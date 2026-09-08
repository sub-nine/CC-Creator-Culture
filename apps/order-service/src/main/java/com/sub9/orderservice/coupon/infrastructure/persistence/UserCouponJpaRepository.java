package com.sub9.orderservice.coupon.infrastructure.persistence;

import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, UUID> {

    @EntityGraph(attributePaths = "coupon")
    Page<UserCoupon> findAllByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "coupon")
    Page<UserCoupon> findAllByUserIdAndStatus(
            UUID userId, UserCouponStatus status, Pageable pageable);

    boolean existsByCoupon_IdAndUserId(UUID couponId, UUID userId);

    // 주문 상태 변경도 같은 영속성 컨텍스트에 있으므로 갱신 뒤 전체 컨텍스트를 비우지 않습니다.
    @Modifying(flushAutomatically = true)
    @Query("""
            update UserCoupon uc
               set uc.status = 'USED', uc.orderId = :orderId, uc.usedAt = :usedAt,
                   uc.updatedAt = :usedAt, uc.updatedBy = uc.userId
             where uc.id = :userCouponId and uc.status = 'ISSUED' and uc.deletedAt is null
               and exists (select c.id from Coupon c where c = uc.coupon
                   and c.deletedAt is null and c.startedAt <= :usedAt and c.expiredAt >= :usedAt)
            """)
    int useIfAvailable(@Param("userCouponId") UUID userCouponId,
            @Param("orderId") UUID orderId, @Param("usedAt") Instant usedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            update UserCoupon uc
               set uc.status = 'ISSUED', uc.orderId = null, uc.usedAt = null,
                   uc.updatedAt = :restoredAt, uc.updatedBy = uc.userId
             where uc.id = :userCouponId and uc.status = 'USED' and uc.orderId = :orderId
            """)
    int restoreIfUsedByOrder(@Param("userCouponId") UUID userCouponId,
            @Param("orderId") UUID orderId, @Param("restoredAt") Instant restoredAt);
}
