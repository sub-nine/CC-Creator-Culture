package com.sub9.orderservice.coupon.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.coupon.application.dto.CouponUpdateCommand;
import com.sub9.orderservice.coupon.application.event.CouponCreatedEvent;
import com.sub9.orderservice.coupon.application.event.CouponDeletedEvent;
import com.sub9.orderservice.coupon.application.event.CouponUpdatedEvent;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
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
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock private ApplicationEventPublisher eventPublisher;
    private CouponCommandService couponCommandService;

    @BeforeEach
    void setUp() {
        couponCommandService = new CouponCommandService(
                couponRepository, uuidV7Generator, Clock.fixed(NOW, ZoneOffset.UTC), eventPublisher);
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
        verify(eventPublisher).publishEvent(new CouponCreatedEvent(COUPON_ID, 100));
    }

    @Test
    @DisplayName("전달된 필드만 변경하고 DB 수정 후 Redis 갱신 이벤트를 발행한다")
    void when_partial_update_is_given_only_requested_values_are_changed() {
        Coupon coupon = coupon();
        CouponUpdateCommand command = new CouponUpdateCommand(
                "수정 쿠폰", null, 200, null, null);
        when(couponRepository.findActiveById(COUPON_ID)).thenReturn(java.util.Optional.of(coupon));
        when(couponRepository.updateIfUnissued(
                org.mockito.ArgumentMatchers.eq(COUPON_ID),
                org.mockito.ArgumentMatchers.eq("수정 쿠폰"),
                org.mockito.ArgumentMatchers.eq(15),
                org.mockito.ArgumentMatchers.eq(200),
                org.mockito.ArgumentMatchers.eq(STARTED_AT),
                org.mockito.ArgumentMatchers.eq(EXPIRED_AT),
                org.mockito.ArgumentMatchers.eq(CREATOR_ID),
                org.mockito.ArgumentMatchers.eq(NOW)))
                .thenReturn(1);

        CouponResponse response = couponCommandService.update(COUPON_ID, command, CREATOR_ID);

        verify(couponRepository).updateIfUnissued(
                COUPON_ID, "수정 쿠폰", 15, 200,
                STARTED_AT, EXPIRED_AT, CREATOR_ID, NOW);
        assertThat(response.totalQuantity()).isEqualTo(200);
        verify(eventPublisher).publishEvent(new CouponUpdatedEvent(COUPON_ID, 200));
    }

    @Test
    @DisplayName("조건부 수정 결과가 0행이면 발급된 쿠폰 변경 오류를 반환한다")
    void when_conditional_update_changes_nothing_not_modifiable_error_is_thrown() {
        Coupon coupon = coupon();
        CouponUpdateCommand command = new CouponUpdateCommand(null, 20, null, null, null);
        when(couponRepository.findActiveById(COUPON_ID)).thenReturn(java.util.Optional.of(coupon));
        when(couponRepository.updateIfUnissued(
                org.mockito.ArgumentMatchers.eq(COUPON_ID),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.eq(CREATOR_ID),
                org.mockito.ArgumentMatchers.eq(NOW)))
                .thenReturn(0);

        assertThatThrownBy(() -> couponCommandService.update(COUPON_ID, command, CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponErrorCode.COUPON_NOT_MODIFIABLE));
    }

    @Test
    @DisplayName("부분 수정 결과의 기간이 올바르지 않으면 거부한다")
    void when_partial_update_makes_invalid_period_update_is_rejected() {
        Coupon coupon = coupon();
        CouponUpdateCommand command = new CouponUpdateCommand(
                null, null, null, EXPIRED_AT.plusSeconds(1), null);
        when(couponRepository.findActiveById(COUPON_ID)).thenReturn(java.util.Optional.of(coupon));

        assertThatThrownBy(() -> couponCommandService.update(COUPON_ID, command, CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponErrorCode.INVALID_COUPON_UPDATE));
    }

    @Test
    @DisplayName("미발급 쿠폰을 조건부 삭제하고 Redis 정리 이벤트를 발행한다")
    void when_coupon_is_unissued_coupon_is_deleted_and_event_is_published() {
        Coupon coupon = coupon();
        when(couponRepository.findActiveById(COUPON_ID)).thenReturn(java.util.Optional.of(coupon));
        when(couponRepository.deleteIfUnissued(COUPON_ID, CREATOR_ID, NOW)).thenReturn(1);

        couponCommandService.delete(COUPON_ID, CREATOR_ID);

        verify(couponRepository).deleteIfUnissued(COUPON_ID, CREATOR_ID, NOW);
        verify(eventPublisher).publishEvent(new CouponDeletedEvent(COUPON_ID));
    }

    @Test
    @DisplayName("조건부 삭제 결과가 0행이면 발급된 쿠폰 삭제 오류를 반환한다")
    void when_conditional_delete_changes_nothing_not_deletable_error_is_thrown() {
        Coupon coupon = coupon();
        when(couponRepository.findActiveById(COUPON_ID)).thenReturn(java.util.Optional.of(coupon));
        when(couponRepository.deleteIfUnissued(COUPON_ID, CREATOR_ID, NOW)).thenReturn(0);

        assertThatThrownBy(() -> couponCommandService.delete(COUPON_ID, CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponErrorCode.COUPON_NOT_DELETABLE));
    }

    private Coupon coupon() {
        return Coupon.create(
                COUPON_ID, "기존 쿠폰", 15, 100,
                STARTED_AT, EXPIRED_AT, CREATOR_ID, NOW.minusSeconds(1));
    }
}
