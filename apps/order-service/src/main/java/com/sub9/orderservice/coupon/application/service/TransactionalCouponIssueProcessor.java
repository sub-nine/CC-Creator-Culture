package com.sub9.orderservice.coupon.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.application.port.CouponIssueProcessor;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
/**
 * 선점이 완료된 쿠폰 발급 요청을 DB에 최종 반영한다.
 *
 * Redis 선점이 완료된 사용자 쿠폰을 하나의 트랜잭션으로 저장한다.
 */
public class TransactionalCouponIssueProcessor implements CouponIssueProcessor {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UuidV7Generator uuidV7Generator;
    private final Clock clock;

    @Override
    @Transactional
    public UUID process(CouponReservation reservation) {
        Instant issueTime = clock.instant();
        Coupon coupon = couponRepository.findActiveById(reservation.couponId())
                .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));

        UserCoupon userCoupon = UserCoupon.issue(
                uuidV7Generator.generate(), coupon, reservation.userId(), issueTime);
        userCouponRepository.save(userCoupon);
        return userCoupon.getId();
    }
}
