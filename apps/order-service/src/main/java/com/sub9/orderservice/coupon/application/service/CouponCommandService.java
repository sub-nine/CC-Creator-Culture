package com.sub9.orderservice.coupon.application.service;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.coupon.application.dto.CouponUpdateCommand;
import com.sub9.orderservice.coupon.application.event.CouponCreatedEvent;
import com.sub9.orderservice.coupon.application.event.CouponUpdatedEvent;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.presentation.request.CreateCouponRequest;
import com.sub9.orderservice.coupon.presentation.response.CouponResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
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

    @Transactional
    public CouponResponse update(
            UUID couponId, CouponUpdateCommand command, UUID updaterId) {
        Coupon coupon = couponRepository.findActiveById(couponId)
                .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));
        String couponName = command.couponName() == null
                ? coupon.getCouponName() : command.couponName();
        int discountRate = command.discountRate() == null
                ? coupon.getDiscountRate() : command.discountRate();
        int totalQuantity = command.totalQuantity() == null
                ? coupon.getTotalQuantity() : command.totalQuantity();
        Instant startedAt = command.startedAt() == null
                ? coupon.getStartedAt() : command.startedAt();
        Instant expiredAt = command.expiredAt() == null
                ? coupon.getExpiredAt() : command.expiredAt();
        validateUpdateValues(couponName, discountRate, totalQuantity, startedAt, expiredAt);
        Instant updatedAt = clock.instant();

        int affectedRows = couponRepository.updateIfUnissued(
                couponId, couponName, discountRate, totalQuantity,
                startedAt, expiredAt, updaterId, updatedAt);
        if (affectedRows == 0) {
            throw new BusinessException(CouponErrorCode.COUPON_NOT_MODIFIABLE);
        }

        eventPublisher.publishEvent(new CouponUpdatedEvent(couponId, totalQuantity));
        return new CouponResponse(
                couponId, couponName, discountRate, totalQuantity, 0, startedAt, expiredAt);
    }

    private void validateUpdateValues(
            String couponName,
            int discountRate,
            int totalQuantity,
            Instant startedAt,
            Instant expiredAt) {
        Objects.requireNonNull(couponName, "쿠폰 이름은 필수입니다.");
        if (couponName.isBlank() || couponName.length() > 100) {
            throw new IllegalArgumentException("쿠폰 이름은 1자 이상 100자 이하여야 합니다.");
        }
        if (discountRate < 1 || discountRate > 100) {
            throw new IllegalArgumentException("할인율은 1 이상 100 이하여야 합니다.");
        }
        if (totalQuantity < 1) {
            throw new IllegalArgumentException("총 발급 수량은 1 이상이어야 합니다.");
        }
        Objects.requireNonNull(startedAt, "쿠폰 시작 시각은 필수입니다.");
        Objects.requireNonNull(expiredAt, "쿠폰 만료 시각은 필수입니다.");
        if (!startedAt.isBefore(expiredAt)) {
            throw new IllegalArgumentException("쿠폰 시작 시각은 만료 시각보다 빨라야 합니다.");
        }
    }
}
