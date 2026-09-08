package com.sub9.orderservice.coupon.application.service;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.application.dto.CouponIssueTarget;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;
import com.sub9.orderservice.coupon.application.exception.CouponReservationReleaseRequiredException;
import com.sub9.orderservice.coupon.application.port.CouponIssueDispatcher;
import com.sub9.orderservice.coupon.application.port.CouponIssueReader;
import com.sub9.orderservice.coupon.application.port.CouponIssueReserver;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private final CouponIssueReader couponIssueReader;
    private final CouponIssueReserver couponIssueReserver;
    private final CouponIssueDispatcher couponIssueDispatcher;
    private final UuidV7Generator uuidV7Generator;
    private final Clock clock;

    public IssueDispatchResult issue(UUID couponId, UUID userId) {
        Objects.requireNonNull(couponId, "쿠폰 식별자는 필수입니다.");
        Objects.requireNonNull(userId, "사용자 식별자는 필수입니다.");

        UUID reservationId = uuidV7Generator.generate();
        Instant requestedAt = clock.instant();
        log.debug("[쿠폰 발급][동기][요청 접수] couponId={} reservationId={}",
                couponId, reservationId);

        CouponIssueTarget target = couponIssueReader.getIssuable(couponId, requestedAt);
        CouponReservation reservation = new CouponReservation(couponId, userId, reservationId);
        couponIssueReserver.reserve(target, reservation);
        log.debug("[쿠폰 발급][동기][Redis 선점 성공] couponId={} reservationId={}",
                couponId, reservationId);

        try {
            return couponIssueDispatcher.dispatch(reservation);
        } catch (CouponReservationReleaseRequiredException exception) {
            rollbackReservation(reservation, exception);
            throw (RuntimeException) exception.getCause();
        }
    }

    private void rollbackReservation(
            CouponReservation reservation, CouponReservationReleaseRequiredException issueFailure) {
        try {
            couponIssueReserver.rollback(reservation);
            log.info("[쿠폰 발급][동기][Redis 보상 완료] couponId={} reservationId={}",
                    reservation.couponId(), reservation.reservationId());
        } catch (RuntimeException releaseFailure) {
            releaseFailure.addSuppressed(issueFailure.getCause());
            log.error("[쿠폰 발급][동기][Redis 보상 실패] couponId={} reservationId={}",
                    reservation.couponId(), reservation.reservationId(), releaseFailure);
            throw releaseFailure;
        }
    }
}
