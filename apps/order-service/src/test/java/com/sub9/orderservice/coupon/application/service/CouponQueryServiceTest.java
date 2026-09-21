package com.sub9.orderservice.coupon.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 조회 서비스")
class CouponQueryServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-05T00:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-09-06T00:00:00Z");
    private static final Instant EXPIRED_AT = Instant.parse("2026-09-07T00:00:00Z");

    @Mock private CouponRepository couponRepository;
    @Mock private UserCouponRepository userCouponRepository;
    private final UuidV7Generator uuidV7Generator = new UuidV7Generator();

    @Test
    @DisplayName("활성 쿠폰 목록을 응답으로 변환한다")
    void when_active_coupons_exist_responses_are_returned() {
        Pageable pageable = PageRequest.of(0, 20);
        Coupon coupon = coupon();
        when(couponRepository.findAllActive(pageable))
                .thenReturn(new PageImpl<>(List.of(coupon), pageable, 1));
        when(userCouponRepository.countByCouponId(coupon.getId())).thenReturn(37L);

        var result = new CouponQueryService(couponRepository, userCouponRepository).findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().couponId()).isEqualTo(coupon.getId());
        assertThat(result.getContent().getFirst().issuedQuantity()).isEqualTo(37);
    }

    @Test
    @DisplayName("활성 쿠폰 ID로 상세 정보를 조회한다")
    void when_active_coupon_exists_detail_is_returned() {
        Coupon coupon = coupon();
        when(couponRepository.findActiveById(coupon.getId())).thenReturn(Optional.of(coupon));
        when(userCouponRepository.countByCouponId(coupon.getId())).thenReturn(42L);

        var result = new CouponQueryService(couponRepository, userCouponRepository)
                .findById(coupon.getId());

        assertThat(result.couponId()).isEqualTo(coupon.getId());
        assertThat(result.issuedQuantity()).isEqualTo(42);
    }

    @Test
    @DisplayName("쿠폰이 없거나 삭제됐으면 쿠폰 없음 오류를 반환한다")
    void when_active_coupon_does_not_exist_not_found_error_is_thrown() {
        UUID couponId = uuidV7Generator.generate();
        when(couponRepository.findActiveById(couponId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CouponQueryService(couponRepository, userCouponRepository)
                .findById(couponId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_NOT_FOUND));
    }

    private Coupon coupon() {
        return Coupon.create(uuidV7Generator.generate(), "조회 쿠폰", 10, 100,
                STARTED_AT, EXPIRED_AT, uuidV7Generator.generate(), CREATED_AT);
    }
}
