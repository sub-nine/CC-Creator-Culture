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
 * 발급 수량 증가와 사용자 쿠폰 저장은 하나의 트랜잭션으로 처리되며,
 * 둘 중 하나라도 실패하면 모든 변경 사항이 롤백된다.
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

        // 발급 기간과 잔여 수량을 조건으로 발급 수량을 원자적으로 1 증가시킨다.
        int affectedRows = couponRepository.increaseIssuedQuantityIfIssuable(
                reservation.couponId(), reservation.userId(), issueTime);
        if (affectedRows == 0) {
            // 조건을 만족하지 못한 원인을 다시 조회하여 클라이언트 오류로 변환한다.
            throw classifyRejectedIssue(reservation, issueTime);
        }

        UserCoupon userCoupon = UserCoupon.issue(
                uuidV7Generator.generate(), coupon, reservation.userId(), issueTime);
        userCouponRepository.save(userCoupon);
        return userCoupon.getId();
    }

    /**
     * 조건부 UPDATE가 처리되지 않은 원인을 현재 DB 상태로 분류한다.
     *
     * 동시 요청으로 사전 검증 이후 상태가 변경될 수 있으므로,
     * UPDATE 결과가 0인 경우 쿠폰 상태를 다시 조회한다.
     */
    private BusinessException classifyRejectedIssue(CouponReservation reservation, Instant issueTime) {
        Coupon current = couponRepository.findActiveById(reservation.couponId())
                .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));
        if (issueTime.isBefore(current.getStartedAt()) || issueTime.isAfter(current.getExpiredAt())) {
            return new BusinessException(CouponErrorCode.NOT_IN_ISSUE_PERIOD);
        }
        if (current.getIssuedQuantity() >= current.getTotalQuantity()) {
            return new BusinessException(CouponErrorCode.SOLD_OUT);
        }
        if (userCouponRepository.existsByCouponIdAndUserId(reservation.couponId(), reservation.userId())) {
            return new BusinessException(CouponErrorCode.ALREADY_ISSUED);
        }
        throw new IllegalStateException("쿠폰 발급 조건부 갱신 실패 원인을 확인할 수 없습니다.");
    }
}
