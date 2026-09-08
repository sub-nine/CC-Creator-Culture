package com.sub9.orderservice.coupon.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.presentation.response.CouponResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponQueryService {
    private final CouponRepository couponRepository;

    public Page<CouponResponse> findAll(Pageable pageable) {
        return couponRepository.findAllActive(pageable).map(CouponResponse::from);
    }

    public CouponResponse findById(UUID couponId) {
        return couponRepository.findActiveById(couponId)
                .map(CouponResponse::from)
                .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));
    }
}
