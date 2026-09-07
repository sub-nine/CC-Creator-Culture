package com.sub9.orderservice.order.application.port.output;

import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.AppliedCoupon;
import java.util.List;
import java.util.UUID;

public interface CouponUsagePort {

    /** 주문과 멱등 성공 결과를 저장하는 로컬 트랜잭션에 참여합니다. */
    void markUsed(UUID orderId, List<AppliedCoupon> appliedCoupons);

    /** 주문 상태 변경과 같은 로컬 트랜잭션에서 사용한 쿠폰을 복구합니다. */
    void restore(UUID orderId, List<UUID> userCouponIds);
}
