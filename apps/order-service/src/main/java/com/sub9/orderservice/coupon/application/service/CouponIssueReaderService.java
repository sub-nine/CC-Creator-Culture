package com.sub9.orderservice.coupon.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.coupon.application.dto.CouponIssueTarget;
import com.sub9.orderservice.coupon.application.port.CouponIssueReader;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponIssueReaderService implements CouponIssueReader {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Override
    public CouponIssueTarget getIssuable(UUID couponId, Instant requestedAt) {
        // Redis 선점 전에 DB를 읽어 명백하게 실패할 요청을 걸러낸다.
        Coupon coupon = couponRepository.findActiveById(couponId)
                .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));
        if (requestedAt.isBefore(coupon.getStartedAt()) || requestedAt.isAfter(coupon.getExpiredAt())) {
            throw new BusinessException(CouponErrorCode.NOT_IN_ISSUE_PERIOD);
        }
        long issuedCount = userCouponRepository.countByCouponId(couponId);
        int remainingQuantity = (int) Math.max(
                0L, coupon.getTotalQuantity() - issuedCount);
        return new CouponIssueTarget(
                coupon.getId(), coupon.getExpiredAt(),
                remainingQuantity);
    }
}
