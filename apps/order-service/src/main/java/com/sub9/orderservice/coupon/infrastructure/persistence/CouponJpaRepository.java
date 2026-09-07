package com.sub9.orderservice.coupon.infrastructure.persistence;

import com.sub9.orderservice.coupon.domain.model.Coupon;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CouponJpaRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByIdAndDeletedAtIsNull(UUID couponId);

    Page<Coupon> findAllByDeletedAtIsNull(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Coupon c
               set c.issuedQuantity = c.issuedQuantity + 1,
                   c.updatedAt = :issuedAt,
                   c.updatedBy = :userId
             where c.id = :couponId
               and c.deletedAt is null
               and c.startedAt <= :issuedAt
               and c.expiredAt >= :issuedAt
               and c.issuedQuantity < c.totalQuantity
            """)
    int increaseIssuedQuantityIfIssuable(
            @Param("couponId") UUID couponId,
            @Param("userId") UUID userId,
            @Param("issuedAt") Instant issuedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Coupon c
               set c.couponName = :couponName,
                   c.discountRate = :discountRate,
                   c.totalQuantity = :totalQuantity,
                   c.startedAt = :startedAt,
                   c.expiredAt = :expiredAt,
                   c.updatedAt = :updatedAt,
                   c.updatedBy = :updaterId
             where c.id = :couponId
               and c.deletedAt is null
               and c.issuedQuantity = 0
            """)
    int updateIfUnissued(
            @Param("couponId") UUID couponId,
            @Param("couponName") String couponName,
            @Param("discountRate") int discountRate,
            @Param("totalQuantity") int totalQuantity,
            @Param("startedAt") Instant startedAt,
            @Param("expiredAt") Instant expiredAt,
            @Param("updaterId") UUID updaterId,
            @Param("updatedAt") Instant updatedAt);
}
