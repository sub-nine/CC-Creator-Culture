package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderItemStatus;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("창작자 주문 상품 상태 변경 서비스")
class OrderItemStatusServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-04T00:00:00Z");
    private static final UUID CREATOR_ID = uuid(1);
    private static final UUID OTHER_CREATOR_ID = uuid(2);

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderItemStatusService orderItemStatusService;

    @Test
    @DisplayName("잠금 조회한 주문 상품 상태를 변경하고 현재 상세를 반환한다")
    void when_order_item_is_found_status_is_changed_and_detail_is_returned() {
        OrderItem item = item(10, CREATOR_ID);
        Order order = order(20, OrderStatus.PAID, item, item(11, OTHER_CREATOR_ID));
        when(orderRepository.findByOrderItemIdForUpdate(item.getId())).thenReturn(Optional.of(order));

        var result = orderItemStatusService.update(
                CREATOR_ID, item.getId(), OrderItemStatus.PREPARING);

        assertThat(result.orderItemId()).isEqualTo(item.getId());
        assertThat(result.orderNumber()).isEqualTo(order.getOrderNumber().toString());
        assertThat(result.status()).isEqualTo(OrderItemStatus.PREPARING);
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(orderRepository).findByOrderItemIdForUpdate(item.getId());
    }

    @Test
    @DisplayName("완료된 주문 상품에 같은 상태를 요청하면 현재 상세를 반환한다")
    void when_completed_status_is_requested_again_current_detail_is_returned() {
        OrderItem item = item(30, CREATOR_ID);
        Order order = order(40, OrderStatus.PAID, item);
        complete(order, item);
        when(orderRepository.findByOrderItemIdForUpdate(item.getId())).thenReturn(Optional.of(order));

        var result = orderItemStatusService.update(
                CREATOR_ID, item.getId(), OrderItemStatus.COMPLETED);

        assertThat(result.status()).isEqualTo(OrderItemStatus.COMPLETED);
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("주문 상품을 찾을 수 없으면 찾을 수 없음 오류를 반환한다")
    void when_order_item_does_not_exist_not_found_is_returned() {
        UUID orderItemId = uuid(50);
        when(orderRepository.findByOrderItemIdForUpdate(orderItemId)).thenReturn(Optional.empty());

        assertError(
                () -> orderItemStatusService.update(
                        CREATOR_ID, orderItemId, OrderItemStatus.PREPARING),
                OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 창작자에게 배정된 주문 상품 변경을 거부한다")
    void when_another_creator_changes_order_item_access_is_denied() {
        OrderItem item = item(60, OTHER_CREATOR_ID);
        Order order = order(70, OrderStatus.PAID, item);
        when(orderRepository.findByOrderItemIdForUpdate(item.getId())).thenReturn(Optional.of(order));

        assertError(
                () -> orderItemStatusService.update(
                        CREATOR_ID, item.getId(), OrderItemStatus.PREPARING),
                OrderErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("결제 완료 또는 처리 중이 아닌 주문의 상품 상태 변경을 거부한다")
    void when_parent_order_is_not_changeable_invalid_order_status_is_returned() {
        OrderItem item = item(80, CREATOR_ID);
        Order order = order(90, OrderStatus.PENDING_PAYMENT, item);
        when(orderRepository.findByOrderItemIdForUpdate(item.getId())).thenReturn(Optional.of(order));

        assertError(
                () -> orderItemStatusService.update(
                        CREATOR_ID, item.getId(), OrderItemStatus.PREPARING),
                OrderErrorCode.INVALID_ORDER_STATUS);
    }

    private static Order order(long sequence, OrderStatus status, OrderItem... items) {
        Order order = Order.create(
                uuid(sequence),
                uuid(sequence + 1_000),
                ShippingAddress.of(
                        "홍길동", "010-1234-5678", "06236", "서울특별시 강남구", "101호"),
                List.of(items),
                CREATED_AT);
        ReflectionTestUtils.setField(order, "status", status);
        ReflectionTestUtils.setField(order, "createdAt", CREATED_AT);
        return order;
    }

    private static OrderItem item(long sequence, UUID creatorId) {
        OrderItem item = OrderItem.create(
                uuid(sequence),
                creatorId,
                uuid(sequence + 2_000),
                uuid(sequence + 3_000),
                null,
                ProductSnapshot.of("아크릴 스탠드", "A 타입", Money.won(18_000), 2),
                Money.won(1_800));
        ReflectionTestUtils.setField(item, "createdAt", CREATED_AT);
        return item;
    }

    private static void complete(Order order, OrderItem item) {
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.PREPARING);
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.SHIPPED);
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.DELIVERED);
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.COMPLETED);
    }

    private static void assertError(ThrowingCallable action, OrderErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isSameAs(expected));
    }

    private static UUID uuid(long sequence) {
        return UUID.fromString("0198f2a0-76c0-7000-8000-%012x".formatted(sequence));
    }
}
