package com.sub9.orderservice.coupon.application.service;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.application.event.CouponCreatedEvent;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.presentation.request.CreateCouponRequest;
import com.sub9.orderservice.coupon.presentation.response.CouponResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponCommandService {
    private final CouponRepository couponRepository;
    private final UuidV7Generator uuidV7Generator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CouponResponse create(CreateCouponRequest request, UUID creatorId) {
        Instant createdAt = clock.instant();
        Coupon coupon = Coupon.create(
                uuidV7Generator.generate(), request.couponName(), request.discountRate(),
                request.totalQuantity(), request.startedAt(), request.expiredAt(),
                creatorId, createdAt);
        Coupon savedCoupon = couponRepository.save(coupon);

        // Redis가 DB보다 먼저 갱신되지 않도록 이벤트만 발행하고, 실제 초기화는 DB 커밋 성공 후 수행한다.
        eventPublisher.publishEvent(new CouponCreatedEvent(
                savedCoupon.getId(), savedCoupon.getTotalQuantity()));

        return CouponResponse.from(savedCoupon);
    }
}
