package com.sub9.orderservice.coupon.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 발급 DB 처리")
class TransactionalCouponIssueProcessorTest {
    private static final Instant ISSUE_TIME = Instant.parse("2026-09-06T00:00:00Z");
    private final UuidV7Generator generator = new UuidV7Generator();
    @Mock private CouponRepository couponRepository;
    @Mock private UserCouponRepository userCouponRepository;
    @Mock private UuidV7Generator uuidV7Generator;

    @Test
    @DisplayName("발급 수량 증가 후 동일한 발급 시각으로 사용자 쿠폰을 저장한다")
    void when_conditional_update_succeeds_user_coupon_is_saved_with_same_issue_time() {
        Coupon coupon = Coupon.create(generator.generate(), "발급 쿠폰", 10, 10,
                ISSUE_TIME.minusSeconds(1), ISSUE_TIME.plusSeconds(60), generator.generate(),
                ISSUE_TIME.minusSeconds(10));
        UUID userId = generator.generate();
        UUID userCouponId = generator.generate();
        CouponReservation reservation = new CouponReservation(coupon.getId(), userId, generator.generate());
        when(couponRepository.findActiveById(coupon.getId())).thenReturn(Optional.of(coupon));
        when(couponRepository.increaseIssuedQuantityIfIssuable(coupon.getId(), userId, ISSUE_TIME))
                .thenReturn(1);
        when(uuidV7Generator.generate()).thenReturn(userCouponId);
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var processor = new TransactionalCouponIssueProcessor(
                couponRepository, userCouponRepository, uuidV7Generator,
                Clock.fixed(ISSUE_TIME, ZoneOffset.UTC));

        UUID result = processor.process(reservation);

        ArgumentCaptor<UserCoupon> captor = ArgumentCaptor.forClass(UserCoupon.class);
        verify(userCouponRepository).save(captor.capture());
        UserCoupon saved = captor.getValue();
        assertThat(result).isEqualTo(userCouponId);
        assertThat(saved.getIssuedAt()).isEqualTo(ISSUE_TIME);
        assertThat(saved.getCreatedAt()).isEqualTo(ISSUE_TIME);
        assertThat(saved.getCreatedBy()).isEqualTo(userId);
    }
}
