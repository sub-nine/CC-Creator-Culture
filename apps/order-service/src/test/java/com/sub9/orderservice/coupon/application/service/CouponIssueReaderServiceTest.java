package com.sub9.orderservice.coupon.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 발급 사전 조회")
class CouponIssueReaderServiceTest {
    private static final Instant STARTED_AT = Instant.parse("2026-09-06T00:00:00Z");
    private static final Instant EXPIRED_AT = Instant.parse("2026-09-07T00:00:00Z");
    private final UuidV7Generator generator = new UuidV7Generator();
    @Mock private CouponRepository couponRepository;

    @Test
    @DisplayName("발급 가능한 쿠폰의 식별자와 만료 시각을 반환한다")
    void when_coupon_is_issuable_target_is_returned() {
        Coupon coupon = coupon(2);
        when(couponRepository.findActiveById(coupon.getId())).thenReturn(Optional.of(coupon));

        var target = new CouponIssueReaderService(couponRepository).getIssuable(coupon.getId(), STARTED_AT);

        assertThat(target.couponId()).isEqualTo(coupon.getId());
        assertThat(target.expiredAt()).isEqualTo(EXPIRED_AT);
    }

    @Test
    @DisplayName("쿠폰이 없으면 쿠폰 없음 오류를 반환한다")
    void when_coupon_does_not_exist_not_found_error_is_thrown() {
        UUID couponId = generator.generate();
        when(couponRepository.findActiveById(couponId)).thenReturn(Optional.empty());

        assertError(() -> new CouponIssueReaderService(couponRepository)
                .getIssuable(couponId, STARTED_AT), CouponErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    @DisplayName("시작 전과 만료 후에는 발급 기간 오류를 반환한다")
    void when_requested_time_is_outside_period_period_error_is_thrown() {
        Coupon coupon = coupon(2);
        when(couponRepository.findActiveById(coupon.getId())).thenReturn(Optional.of(coupon));
        CouponIssueReaderService reader = new CouponIssueReaderService(couponRepository);

        assertError(() -> reader.getIssuable(coupon.getId(), STARTED_AT.minusNanos(1)),
                CouponErrorCode.NOT_IN_ISSUE_PERIOD);
        assertError(() -> reader.getIssuable(coupon.getId(), EXPIRED_AT.plusNanos(1)),
                CouponErrorCode.NOT_IN_ISSUE_PERIOD);
    }

    @Test
    @DisplayName("발급 수량이 소진되면 품절 오류를 반환한다")
    void when_quantity_is_exhausted_sold_out_error_is_thrown() {
        Coupon coupon = coupon(1);
        coupon.issue(generator.generate(), STARTED_AT);
        when(couponRepository.findActiveById(coupon.getId())).thenReturn(Optional.of(coupon));

        assertError(() -> new CouponIssueReaderService(couponRepository)
                .getIssuable(coupon.getId(), STARTED_AT), CouponErrorCode.SOLD_OUT);
    }

    private Coupon coupon(int totalQuantity) {
        return Coupon.create(generator.generate(), "발급 쿠폰", 10, totalQuantity,
                STARTED_AT, EXPIRED_AT, generator.generate(), STARTED_AT.minusSeconds(1));
    }

    private void assertError(Runnable action, CouponErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
