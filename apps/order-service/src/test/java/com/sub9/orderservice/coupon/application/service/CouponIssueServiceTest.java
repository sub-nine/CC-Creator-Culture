package com.sub9.orderservice.coupon.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.application.dto.CouponIssueTarget;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;
import com.sub9.orderservice.coupon.application.port.CouponIssueDispatcher;
import com.sub9.orderservice.coupon.application.port.CouponIssueReader;
import com.sub9.orderservice.coupon.application.port.CouponIssueReserver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 발급 흐름")
class CouponIssueServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");
    @Mock private CouponIssueReader reader;
    @Mock private CouponIssueReserver reserver;
    @Mock private CouponIssueDispatcher dispatcher;
    private final UuidV7Generator generator = new UuidV7Generator();
    private CouponIssueService service;

    @BeforeEach
    void setUp() {
        service = new CouponIssueService(reader, reserver, dispatcher, generator,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("조회와 선점 후 동기 처리기로 발급을 전달한다")
    void when_coupon_is_issuable_issue_reserves_and_dispatches_in_order() {
        UUID couponId = generator.generate();
        UUID userId = generator.generate();
        CouponIssueTarget target = new CouponIssueTarget(couponId, NOW.plusSeconds(600));
        var completed = new IssueDispatchResult.Completed(generator.generate());
        given(reader.getIssuable(couponId, NOW)).willReturn(target);
        given(dispatcher.dispatch(ArgumentMatchers.any())).willReturn(completed);

        assertThat(service.issue(couponId, userId)).isEqualTo(completed);

        ArgumentCaptor<CouponReservation> captor = ArgumentCaptor.forClass(CouponReservation.class);
        InOrder order = inOrder(reader, reserver, dispatcher);
        order.verify(reader).getIssuable(couponId, NOW);
        order.verify(reserver).reserve(ArgumentMatchers.eq(target), captor.capture());
        order.verify(dispatcher).dispatch(captor.getValue());
        assertThat(captor.getValue().couponId()).isEqualTo(couponId);
        assertThat(captor.getValue().userId()).isEqualTo(userId);
        assertThat(captor.getValue().reservationId().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("조회가 실패하면 선점과 발급 처리를 실행하지 않는다")
    void when_read_fails_issue_stops() {
        UUID couponId = generator.generate();
        UUID userId = generator.generate();
        RuntimeException failure = new RuntimeException("조회 실패");
        given(reader.getIssuable(couponId, NOW)).willThrow(failure);
        assertThatThrownBy(() -> service.issue(couponId, userId)).isSameAs(failure);
        verify(reserver, never()).reserve(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(dispatcher, never()).dispatch(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("선점이 실패하면 발급 처리를 실행하지 않는다")
    void when_reservation_fails_issue_stops() {
        UUID couponId = generator.generate();
        UUID userId = generator.generate();
        CouponIssueTarget target = new CouponIssueTarget(couponId, NOW.plusSeconds(600));
        RuntimeException failure = new RuntimeException("선점 실패");
        given(reader.getIssuable(couponId, NOW)).willReturn(target);
        doThrow(failure).when(reserver)
                .reserve(ArgumentMatchers.eq(target), ArgumentMatchers.any());
        assertThatThrownBy(() -> service.issue(couponId, userId)).isSameAs(failure);
        verify(dispatcher, never()).dispatch(ArgumentMatchers.any());
    }
}
