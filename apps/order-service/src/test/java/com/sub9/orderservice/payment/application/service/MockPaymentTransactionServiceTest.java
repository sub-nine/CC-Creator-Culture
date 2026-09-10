package com.sub9.orderservice.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sub9.orderservice.order.application.service.OrderPaymentResultService;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderItemStatus;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import com.sub9.orderservice.payment.application.dto.MockPaymentResult;
import com.sub9.orderservice.payment.domain.model.Payment;
import com.sub9.orderservice.payment.domain.model.PaymentMethod;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import com.sub9.orderservice.payment.domain.repository.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("모의 결제 처리")
class MockPaymentTransactionServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-07T00:00:00Z");
    private final UuidV7Generator ids = new UuidV7Generator();

    @Mock private OrderRepository orders;
    @Mock private PaymentRepository payments;
    @Mock private CouponUsagePort coupons;
    @Mock private Clock clock;
    @Mock private org.springframework.context.ApplicationEventPublisher events;

    private MockPaymentTransactionService service;

    @BeforeEach
    void setUp() {
        service = new MockPaymentTransactionService(
                orders, payments, new OrderPaymentResultService(orders, coupons, events, ids), clock, ids);
    }

    @ParameterizedTest
    @CsvSource({"SUCCESS, 34200", "FAILED, 34200", "SUCCESS, 0", "FAILED, 0"})
    @DisplayName("주문 금액으로 결제를 만들고 성공 또는 실패를 주문에 반영한다")
    void when_payment_is_requested_order_amount_and_result_are_applied(PaymentStatus status, long amount) {
        Order order = order(amount);
        Instant now = CREATED_AT.plusSeconds(10).plusNanos(123456789);
        givenNewOrder(order, now);
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var processed = service.process(order.getCustomerId(), order.getOrderNumber(), status);

        var calls = inOrder(orders, clock);
        calls.verify(orders).findByOrderNumberForUpdate(order.getOrderNumber());
        calls.verify(clock).instant();
        calls.verify(orders).findByIdForUpdate(order.getId());
        assertThat(processed.result().paymentId()).isNotNull();
        assertThat(processed.result().orderNumber()).isEqualTo(order.getOrderNumber().toString());
        assertThat(processed.result().method()).isEqualTo(PaymentMethod.MOCK);
        assertThat(processed.result().status()).isEqualTo(status);
        assertThat(processed.result().amount()).isEqualTo(amount);
        assertThat(processed.result().processedAt()).isEqualTo(now.truncatedTo(ChronoUnit.MICROS));
        if (status == PaymentStatus.SUCCESS) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getPaidAt()).isEqualTo(processed.result().processedAt());
            assertThat(processed.result().failureCode()).isNull();
            assertThat(processed.stockRestore()).isNull();
            verifyNoInteractions(coupons);
        } else {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(order.getPaidAt()).isNull();
            assertThat(processed.result().failureCode()).isEqualTo("MOCK_PAYMENT_FAILED");
            assertThat(processed.stockRestore().reason()).isEqualTo(RestoreReason.PAYMENT_FAILED);
            assertThat(processed.stockRestore().items()).containsExactly(
                    new StockItem(order.getItems().getFirst().getSkuId(), 2));
            verify(coupons).restore(order.getId(), List.of(order.getItems().getFirst().getUserCouponId()));
        }
    }

    @Test
    @DisplayName("주문이 없으면 결제를 처리하지 않는다")
    void when_order_is_missing_payment_is_rejected() {
        Order order = order(100);
        assertError(order, OrderErrorCode.ORDER_NOT_FOUND);
        verifyNoInteractions(payments, coupons, clock);
    }

    @Test
    @DisplayName("다른 사용자의 주문은 결제를 처리하지 않는다")
    void when_customer_does_not_own_order_payment_is_rejected() {
        Order order = order(100);
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.process(ids.generate(), order.getOrderNumber(), PaymentStatus.SUCCESS))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_ACCESS_DENIED));
        verifyNoInteractions(payments, coupons, clock);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    @DisplayName("잠금을 얻었을 때 결제 기한에 도달했다면 결제를 처리하지 않는다")
    void when_lock_is_acquired_at_or_after_deadline_payment_is_rejected(long elapsedNanos) {
        Order order = order(100);
        givenNewOrder(order, order.getExpiresAt().plusNanos(elapsedNanos));

        assertError(order, OrderErrorCode.ORDER_ALREADY_EXPIRED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(payments, never()).save(any());
        verifyNoInteractions(coupons);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "FAILED", "EXPIRED"})
    @DisplayName("이미 결제되거나 만료된 주문은 새로운 결제를 처리하지 않는다")
    void when_order_is_no_longer_pending_new_payment_is_rejected(OrderStatus status) {
        Order order = order(100);
        if (status == OrderStatus.PAID) order.markPaid(CREATED_AT.plusSeconds(1));
        if (status == OrderStatus.FAILED) order.markPaymentFailed(CREATED_AT.plusSeconds(1));
        if (status == OrderStatus.EXPIRED) order.expire(order.getExpiresAt());
        givenNewOrder(order, order.getExpiresAt().plusSeconds(1));

        assertError(order, status == OrderStatus.EXPIRED
                ? OrderErrorCode.ORDER_ALREADY_EXPIRED : OrderErrorCode.INVALID_ORDER_STATUS);
        verify(payments, never()).save(any());
        verifyNoInteractions(coupons);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("같은 결제 요청은 현재 주문 상태와 관계없이 저장된 결과를 반환한다")
    void when_same_result_is_requested_stored_payment_is_returned(PaymentStatus status) {
        Order order = order(34200);
        Payment original = Payment.create(ids.generate(), order.getId(), order.getPaymentAmount(),
                status, CREATED_AT.plusSeconds(1));
        if (status == PaymentStatus.SUCCESS) {
            order.markPaid(original.getProcessedAt());
            OrderItem item = order.getItems().getFirst();
            order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.PREPARING);
        } else {
            order.markPaymentFailed(original.getProcessedAt());
        }
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderId(order.getId())).thenReturn(Optional.of(original));

        var repeated = service.process(order.getCustomerId(), order.getOrderNumber(), status);

        assertThat(repeated.result()).isEqualTo(MockPaymentResult.from(original, order.getOrderNumber()));
        assertThat(repeated.stockRestore()).isNull();
        verify(payments, never()).save(any());
        verify(orders, never()).findByIdForUpdate(any());
        verifyNoInteractions(coupons, clock, events);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("저장된 결제와 반대 결과를 요청하면 기존 기록을 유지하고 거부한다")
    void when_opposite_result_is_requested_existing_payment_is_unchanged(PaymentStatus status) {
        Order order = order(34200);
        Payment original = Payment.create(ids.generate(), order.getId(), order.getPaymentAmount(),
                status, CREATED_AT.plusSeconds(1));
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderId(order.getId())).thenReturn(Optional.of(original));
        PaymentStatus opposite = status == PaymentStatus.SUCCESS ? PaymentStatus.FAILED : PaymentStatus.SUCCESS;

        assertThatThrownBy(() -> service.process(order.getCustomerId(), order.getOrderNumber(), opposite))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS));

        assertThat(original.getStatus()).isEqualTo(status);
        verify(payments, never()).save(any());
        verify(orders, never()).findByIdForUpdate(any());
        verifyNoInteractions(coupons, clock, events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"customerId", "orderNumber", "result"})
    @DisplayName("필수 입력이 없으면 주문과 결제를 조회하지 않는다")
    void when_required_input_is_missing_payment_is_not_processed(String missing) {
        Order order = order(100);
        assertThatThrownBy(() -> service.process(
                missing.equals("customerId") ? null : order.getCustomerId(),
                missing.equals("orderNumber") ? null : order.getOrderNumber(),
                missing.equals("result") ? null : PaymentStatus.SUCCESS))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(orders, payments, coupons, clock);
    }

    private void givenNewOrder(Order order, Instant processedAt) {
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(clock.instant()).thenReturn(processedAt);
    }

    private void assertError(Order order, OrderErrorCode expected) {
        assertThatThrownBy(() -> service.process(order.getCustomerId(), order.getOrderNumber(), PaymentStatus.SUCCESS))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(expected));
    }

    private Order order(long amount) {
        return Order.create(ids.generate(), ids.generate(),
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울 강남구", "101호"),
                List.of(OrderItem.create(ids.generate(), null, ids.generate(), ids.generate(), ids.generate(),
                        ids.generate(), ProductSnapshot.of("키링", "기본", Money.won(20_000), 2),
                        Money.won(40_000 - amount))), CREATED_AT);
    }
}
