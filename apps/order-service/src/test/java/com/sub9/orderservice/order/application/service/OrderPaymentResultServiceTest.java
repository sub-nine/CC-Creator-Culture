package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
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
@DisplayName("주문 결제 결과 반영 서비스")
class OrderPaymentResultServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-04T00:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CouponUsagePort couponUsagePort;

    @InjectMocks
    private OrderPaymentResultService paymentResultService;

    @Test
    @DisplayName("잠근 주문에 결제 성공 상태와 처리 시각을 반영한다")
    void when_payment_succeeds_locked_order_is_marked_paid() {
        Order order = order(10, item(11, null));
        Instant processedAt = order.getExpiresAt().minusSeconds(1);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        paymentResultService.markPaid(order.getId(), processedAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isEqualTo(processedAt);
        verifyNoInteractions(couponUsagePort);
    }

    @Test
    @DisplayName("결제 실패 상태와 중복을 제거한 쿠폰 복구를 반영하고 재고 복구 명령을 반환한다")
    void when_payment_fails_order_and_coupons_are_updated_and_stock_command_is_returned() {
        UUID userCouponId = uuid(100);
        Order order = order(20, item(21, userCouponId), item(22, userCouponId));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        var command = paymentResultService.markPaymentFailed(
                order.getId(), order.getExpiresAt().minusSeconds(1));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(command.orderId()).isEqualTo(order.getId());
        assertThat(command.reason()).isEqualTo(RestoreReason.PAYMENT_FAILED);
        assertThat(command.items())
                .extracting(item -> item.skuId(), item -> item.quantity())
                .containsExactly(
                        tuple(uuid(3_021), 2),
                        tuple(uuid(3_022), 2));
        verify(couponUsagePort).restore(order.getId(), List.of(userCouponId));
    }

    @Test
    @DisplayName("쿠폰을 사용하지 않은 주문은 쿠폰 복구 없이 재고 복구 명령을 반환한다")
    void when_order_has_no_coupon_coupon_restore_is_skipped() {
        Order order = order(30, item(31, null));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        var command = paymentResultService.markPaymentFailed(
                order.getId(), order.getExpiresAt().minusSeconds(1));

        assertThat(command.items()).hasSize(1);
        verifyNoInteractions(couponUsagePort);
    }

    @Test
    @DisplayName("주문을 찾을 수 없으면 상태와 자원을 변경하지 않는다")
    void when_order_does_not_exist_not_found_is_returned() {
        UUID orderId = uuid(40);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertOrderError(
                () -> paymentResultService.markPaid(orderId, CREATED_AT),
                OrderErrorCode.ORDER_NOT_FOUND);
        verifyNoInteractions(couponUsagePort);
    }

    @Test
    @DisplayName("결제 기한에 도달한 주문에는 실패 상태와 쿠폰 복구를 반영하지 않는다")
    void when_payment_window_has_expired_payment_failure_is_rejected() {
        Order order = order(45, item(46, uuid(145)));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertOrderError(
                () -> paymentResultService.markPaymentFailed(order.getId(), order.getExpiresAt()),
                OrderErrorCode.ORDER_ALREADY_EXPIRED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verifyNoInteractions(couponUsagePort);
    }

    @Test
    @DisplayName("이미 결제된 주문에는 실패 상태와 쿠폰 복구를 반영하지 않는다")
    void when_order_is_already_paid_payment_failure_is_rejected() {
        Order order = order(50, item(51, uuid(150)));
        order.markPaid(order.getExpiresAt().minusSeconds(2));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertOrderError(
                () -> paymentResultService.markPaymentFailed(
                        order.getId(), order.getExpiresAt().minusSeconds(1)),
                OrderErrorCode.INVALID_ORDER_STATUS);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
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
                uuid(sequence),
                uuid(sequence + 2_000),
                uuid(sequence + 2_500),
                uuid(sequence + 3_000),
                userCouponId,
                ProductSnapshot.of("아크릴 스탠드", "A 타입", Money.won(18_000), 2),
                Money.won(1_800));
    }

    private static void assertOrderError(Runnable action, OrderErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isSameAs(expected));
    }

    private static UUID uuid(long sequence) {
        return UUID.fromString("0198f2a0-76c0-7000-8000-%012x".formatted(sequence));
    }
}
