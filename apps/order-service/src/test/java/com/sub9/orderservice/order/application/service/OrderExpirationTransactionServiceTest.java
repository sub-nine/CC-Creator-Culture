package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("주문 만료 트랜잭션 서비스")
class OrderExpirationTransactionServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-04T00:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CouponUsagePort couponUsagePort;

    @InjectMocks
    private OrderExpirationTransactionService expirationService;

    @Test
    @DisplayName("잠근 결제 대기 주문을 만료하고 쿠폰과 재고 복구 정보를 반환한다")
    void when_pending_order_has_expired_order_and_coupons_are_updated() {
        UUID userCouponId = uuid(100);
        Order order = order(10, item(11, userCouponId));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        var result = expirationService.expire(order.getId(), order.getExpiresAt());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().reason()).isEqualTo(RestoreReason.ORDER_EXPIRED);
        assertThat(result.orElseThrow().items()).hasSize(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(couponUsagePort).restore(order.getId(), List.of(userCouponId));
    }

    @Test
    @DisplayName("결제 기한 전 주문은 상태와 자원을 변경하지 않는다")
    void when_payment_window_has_not_expired_order_is_skipped() {
        Order order = order(20, item(21, uuid(120)));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        var result = expirationService.expire(
                order.getId(), order.getExpiresAt().minusNanos(1));

        assertThat(result).isEmpty();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verifyNoInteractions(couponUsagePort);
    }

    @Test
    @DisplayName("결제가 먼저 완료된 주문은 만료 상태로 덮어쓰지 않는다")
    void when_payment_wins_order_expiration_is_skipped() {
        Order order = order(30, item(31, uuid(130)));
        order.markPaid(order.getExpiresAt().minusSeconds(1));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        var result = expirationService.expire(
                order.getId(), order.getExpiresAt().plusSeconds(1));

        assertThat(result).isEmpty();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(couponUsagePort);
    }

    @Test
    @DisplayName("후보 주문이 사라졌으면 자원 변경 없이 건너뛴다")
    void when_candidate_does_not_exist_order_is_skipped() {
        UUID orderId = uuid(40);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThat(expirationService.expire(orderId, CREATED_AT)).isEmpty();
        verifyNoInteractions(couponUsagePort);
    }

    private static Order order(long sequence, OrderItem... items) {
        return Order.create(
                uuid(sequence),
                uuid(sequence + 1_000),
                ShippingAddress.of(
                        "홍길동", "010-1234-5678", "06236", "서울특별시 강남구", "101호"),
                List.of(items),
                CREATED_AT);
    }

    private static OrderItem item(long sequence, UUID userCouponId) {
        return OrderItem.create(
                uuid(sequence), null,
                uuid(sequence + 2_000),
                uuid(sequence + 2_500),
                uuid(sequence + 3_000),
                userCouponId,
                ProductSnapshot.of("아크릴 스탠드", "A 타입", Money.won(18_000), 2),
                Money.won(1_800));
    }

    private static UUID uuid(long sequence) {
        return UUID.fromString("0198f2a0-76c0-7000-8000-%012x".formatted(sequence));
    }
}
