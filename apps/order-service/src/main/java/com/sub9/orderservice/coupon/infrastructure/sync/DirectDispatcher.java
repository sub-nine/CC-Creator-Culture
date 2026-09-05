package com.sub9.orderservice.coupon.infrastructure.sync;

import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;
import com.sub9.orderservice.coupon.application.port.CouponIssueDispatcher;
import com.sub9.orderservice.coupon.application.port.CouponIssueProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DirectDispatcher implements CouponIssueDispatcher {
    // Processor를 즉시 호출하고 Completed 결과를 반환
    /* 향후 Kafka에서는 다른 Dispatcher가 메시지를 보내고
    * Consumer가 같은 Processor 계약을 사용 */

    private final CouponIssueProcessor couponIssueProcessor;

    @Override
    public IssueDispatchResult dispatch(CouponReservation reservation) {
        long startedAt = System.nanoTime();
        var userCouponId = couponIssueProcessor.process(reservation);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        log.info(
                "[쿠폰 발급][동기][DB 커밋 완료] couponId={} reservationId={} 처리시간={}ms",
                reservation.couponId(), reservation.reservationId(), elapsedMillis);
        return new IssueDispatchResult.Completed(userCouponId);
    }
}
