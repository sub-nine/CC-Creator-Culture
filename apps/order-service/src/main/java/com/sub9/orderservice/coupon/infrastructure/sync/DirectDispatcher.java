package com.sub9.orderservice.coupon.infrastructure.sync;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.coupon.application.dto.CouponIssueFailureType;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;
import com.sub9.orderservice.coupon.application.exception.CouponReservationReleaseRequiredException;
import com.sub9.orderservice.coupon.application.port.CouponIssueDispatcher;
import com.sub9.orderservice.coupon.application.port.CouponIssueProcessor;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.infrastructure.persistence.CouponIssueFailureClassifier;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectDispatcher implements CouponIssueDispatcher {
    // Processor를 즉시 호출하고 Completed 결과를 반환
    /* 향후 Kafka에서는 다른 Dispatcher가 메시지를 보내고
    * Consumer가 같은 Processor 계약을 사용 */

    private final CouponIssueProcessor couponIssueProcessor;
    private final CouponIssueFailureClassifier failureClassifier;

    @Override
    public IssueDispatchResult dispatch(CouponReservation reservation) {
        long startedAt = System.nanoTime();
        final UUID userCouponId;
        try {
            userCouponId = couponIssueProcessor.process(reservation);
        } catch (RuntimeException exception) {
            throw classifiedException(exception);
        }
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        log.info(
                "[쿠폰 발급][동기][DB 커밋 완료] couponId={} reservationId={} 처리시간={}ms",
                reservation.couponId(), reservation.reservationId(), elapsedMillis);
        return new IssueDispatchResult.Completed(userCouponId);
    }

    private RuntimeException classifiedException(RuntimeException exception) {
        CouponIssueFailureType failureType = failureClassifier.classify(exception);
        return switch (failureType) {
            case ALREADY_ISSUED -> new BusinessException(CouponErrorCode.ALREADY_ISSUED);
            case RELEASE_REQUIRED -> new CouponReservationReleaseRequiredException(exception);
            case RESULT_UNKNOWN -> exception;
        };
    }
}
