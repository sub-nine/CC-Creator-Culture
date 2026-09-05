package com.sub9.orderservice.coupon.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.presentation.request.CreateCouponRequest;
import com.sub9.orderservice.coupon.presentation.response.CouponResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 명령 서비스")
class CouponCommandServiceTest {
    private static final UUID COUPON_ID = UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID CREATOR_ID = UUID.fromString("01990a00-0000-7000-8000-000000000002");
    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-09-06T00:00:00Z");
    private static final Instant EXPIRED_AT = Instant.parse("2026-09-07T00:00:00Z");

    @Mock private CouponRepository couponRepository;
    @Mock private UuidV7Generator uuidV7Generator;
    private CouponCommandService couponCommandService;

    @BeforeEach
    void setUp() {
        couponCommandService = new CouponCommandService(
                couponRepository, uuidV7Generator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("유효한 요청으로 발급 수량이 0인 쿠폰과 생성 감사를 저장한다")
    void when_valid_request_is_given_coupon_is_created_with_zero_quantity_and_audit() {
        when(uuidV7Generator.generate()).thenReturn(COUPON_ID);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateCouponRequest request = new CreateCouponRequest(
                " 트렌드 15% 할인 쿠폰 ", 15, 100, STARTED_AT, EXPIRED_AT);

        CouponResponse response = couponCommandService.create(request, CREATOR_ID);

        ArgumentCaptor<Coupon> couponCaptor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(couponCaptor.capture());
        Coupon saved = couponCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(COUPON_ID);
        assertThat(saved.getCouponName()).isEqualTo("트렌드 15% 할인 쿠폰");
        assertThat(saved.getIssuedQuantity()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo(CREATOR_ID);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(response.couponId()).isEqualTo(COUPON_ID);
        assertThat(response.issuedQuantity()).isZero();
    }
}
