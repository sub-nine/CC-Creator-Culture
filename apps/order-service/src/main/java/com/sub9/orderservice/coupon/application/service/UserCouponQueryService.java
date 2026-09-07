package com.sub9.orderservice.coupon.application.service;

import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import com.sub9.orderservice.coupon.presentation.response.UserCouponResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserCouponQueryService {

    private final UserCouponRepository userCouponRepository;
    private final Clock clock;

    public Page<UserCouponResponse> findAll(
            UUID userId, UserCouponStatus status, Pageable pageable) {
        Instant queriedAt = clock.instant();
        return userCouponRepository.findAllByUserId(userId, status, pageable)
                .map(userCoupon -> UserCouponResponse.from(userCoupon, queriedAt));
    }
}
