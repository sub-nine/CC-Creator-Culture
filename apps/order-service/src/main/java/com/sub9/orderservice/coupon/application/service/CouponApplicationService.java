package com.sub9.orderservice.coupon.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponApplicationService implements CouponApplicationPort {

    private final UserCouponRepository userCouponRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<AppliedCoupon> apply(UUID customerId, List<CouponApplicationRequest> requests) {
        Instant now = clock.instant();
        var usedIds = new HashSet<UUID>();
        var applied = new ArrayList<AppliedCoupon>(requests.size());
        for (CouponApplicationRequest request : requests) {
            UserCoupon userCoupon = userCouponRepository.findById(request.userCouponId())
                    .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));
            Coupon coupon = userCoupon.getCoupon();
            if (!usedIds.add(userCoupon.getId())
                    || !userCoupon.getUserId().equals(customerId)
                    || userCoupon.isDeleted() || coupon.isDeleted()
                    || userCoupon.getStatus() != UserCouponStatus.ISSUED
                    || now.isBefore(coupon.getStartedAt()) || now.isAfter(coupon.getExpiredAt())) {
                throw new BusinessException(CouponErrorCode.COUPON_NOT_USABLE);
            }
            long amount = request.originalAmount();
            int rate = coupon.getDiscountRate();
            // 원 미만은 버리고, 금액에 할인율을 먼저 곱할 때의 오버플로를 피합니다.
            long discount = amount / 100 * rate + amount % 100 * rate / 100;
            applied.add(new AppliedCoupon(request.cartItemId(), userCoupon.getId(), discount));
        }
        return List.copyOf(applied);
    }
}
