package com.sub9.orderservice.coupon.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.AppliedCoupon;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CouponUsageService implements CouponUsagePort {

    private final UserCouponRepository userCouponRepository;
    private final Clock clock;

    @Override
    public void markUsed(UUID orderId, List<AppliedCoupon> appliedCoupons) {
        Objects.requireNonNull(orderId, "주문 식별자는 필수입니다.");
        Instant now = clock.instant();
        // 여러 쿠폰을 사용한 주문끼리도 같은 순서로 행을 잠급니다.
        for (UUID id : appliedCoupons.stream().map(AppliedCoupon::userCouponId).sorted().toList()) {
            if (userCouponRepository.useIfAvailable(id, orderId, now) != 1) {
                throw new BusinessException(CouponErrorCode.COUPON_NOT_USABLE);
            }
        }
    }

    @Override
    public void restore(UUID orderId, List<UUID> userCouponIds) {
        Objects.requireNonNull(orderId, "주문 식별자는 필수입니다.");
        Instant now = clock.instant();
        for (UUID id : userCouponIds.stream().sorted().toList()) {
            userCouponRepository.restoreIfUsedByOrder(id, orderId, now);
        }
    }
}
