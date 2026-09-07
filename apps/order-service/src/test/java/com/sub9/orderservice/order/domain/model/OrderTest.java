package com.sub9.orderservice.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("주문 애그리거트")
class OrderTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Test
    @DisplayName("여러 주문 상품으로 결제 대기 주문을 생성하고 금액을 계산한다")
    void when_valid_items_are_given_order_is_created_with_calculated_totals() {
        OrderItem first = item(uuidGenerator.generate(), 10_000, 2, 1_000);
        OrderItem second = item(uuidGenerator.generate(), 5_000, 1, 0);

        Order order = order(List.of(first, second));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getOriginalAmount()).isEqualTo(Money.won(25_000));
        assertThat(order.getDiscountAmount()).isEqualTo(Money.won(1_000));
        assertThat(order.getPaymentAmount()).isEqualTo(Money.won(24_000));
        assertThat(order.getExpiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(order.getItems()).containsExactly(first, second);
        assertThat(first.getOrderId()).isEqualTo(order.getId());
    }

    @Test
    @DisplayName("빈 주문 상품과 중복 SKU를 거부한다")
    void when_items_are_empty_or_sku_is_duplicated_order_creation_is_rejected() {
        assertOrderError(() -> order(List.of()), OrderErrorCode.INVALID_ORDER_ITEMS);

        UUID skuId = uuidGenerator.generate();
        OrderItem first = item(skuId, 10_000, 1, 0);
        OrderItem second = item(skuId, 10_000, 1, 0);
        assertOrderError(() -> order(List.of(first, second)), OrderErrorCode.INVALID_ORDER_ITEMS);
    }

    @Test
    @DisplayName("UUID v7이 아닌 주문 식별자를 거부한다")
    void when_order_id_is_not_uuid_v7_creation_is_rejected() {
        assertThatThrownBy(() -> Order.create(
                UUID.randomUUID(),
                uuidGenerator.generate(),
                shippingAddress(),
                List.of(item(uuidGenerator.generate(), 1_000, 1, 0)),
                NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("식별자는 UUID v7 형식이어야 합니다.");
    }

    @Test
    @DisplayName("창작자가 주문 상품을 순서대로 변경하면 상위 주문 상태를 함께 변경한다")
    void when_creator_changes_item_status_in_order_parent_status_is_recalculated() {
        OrderItem item = item(uuidGenerator.generate(), 10_000, 1, 0);
        Order order = paidOrder(List.of(item));

        assertThat(order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.PREPARING))
                .isSameAs(item);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);

        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.SHIPPED);
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.DELIVERED);
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.COMPLETED);

        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("모든 주문 상품이 완료될 때까지 상위 주문은 처리 중 상태를 유지한다")
    void when_only_some_items_are_completed_parent_order_remains_processing() {
        OrderItem first = item(uuidGenerator.generate(), 10_000, 1, 0);
        OrderItem second = item(uuidGenerator.generate(), 5_000, 1, 0);
        Order order = paidOrder(List.of(first, second));

        complete(order, first);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);

        complete(order, second);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("완료된 주문 상품에 같은 상태를 요청하면 현재 결과를 반환한다")
    void when_same_completed_status_is_requested_current_result_is_returned() {
        OrderItem item = item(uuidGenerator.generate(), 10_000, 1, 0);
        Order order = paidOrder(List.of(item));
        complete(order, item);

        assertThat(order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.COMPLETED))
                .isSameAs(item);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("순서를 건너뛰거나 창작자가 지정할 수 없는 상태를 거부한다")
    void when_invalid_item_status_is_requested_transition_is_rejected() {
        OrderItem item = item(uuidGenerator.generate(), 10_000, 1, 0);
        Order order = paidOrder(List.of(item));

        assertOrderError(
                () -> order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.SHIPPED),
                OrderErrorCode.INVALID_ORDER_ITEM_STATUS_TRANSITION);
        assertOrderError(
                () -> order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.ORDERED),
                OrderErrorCode.INVALID_ORDER_ITEM_STATUS_TRANSITION);
        assertOrderError(
                () -> order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.CANCELED),
                OrderErrorCode.INVALID_ORDER_ITEM_STATUS_TRANSITION);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.ORDERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("주문 상품 상태의 역방향 변경을 거부한다")
    void when_previous_item_status_is_requested_transition_is_rejected() {
        OrderItem item = item(uuidGenerator.generate(), 10_000, 1, 0);
        Order order = paidOrder(List.of(item));
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.PREPARING);
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.SHIPPED);

        assertOrderError(
                () -> order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.PREPARING),
                OrderErrorCode.INVALID_ORDER_ITEM_STATUS_TRANSITION);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.SHIPPED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    @DisplayName("다른 창작자에게 배정된 주문 상품 변경을 거부한다")
    void when_another_creator_changes_item_access_is_denied() {
        OrderItem item = item(uuidGenerator.generate(), 10_000, 1, 0);
        Order order = paidOrder(List.of(item));
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.PREPARING);

        assertOrderError(
                () -> order.changeItemStatus(
                        uuidGenerator.generate(), item.getId(), item.getStatus()),
                OrderErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("주문에 포함되지 않은 상품 변경을 거부한다")
    void when_item_is_not_in_order_not_found_is_returned() {
        Order order = paidOrder(List.of(item(uuidGenerator.generate(), 10_000, 1, 0)));

        assertOrderError(
                () -> order.changeItemStatus(
                        uuidGenerator.generate(), uuidGenerator.generate(), OrderItemStatus.PREPARING),
                OrderErrorCode.ORDER_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING_PAYMENT", "COMPLETED", "EXPIRED", "FAILED", "CANCELED"})
    @DisplayName("결제 완료 또는 처리 중이 아닌 주문의 상품 상태 변경을 거부한다")
    void when_parent_status_is_not_changeable_item_status_change_is_rejected(OrderStatus status) {
        OrderItem item = item(uuidGenerator.generate(), 10_000, 1, 0);
        Order order = order(List.of(item));
        ReflectionTestUtils.setField(order, "status", status);

        assertOrderError(
                () -> order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.PREPARING),
                OrderErrorCode.INVALID_ORDER_STATUS);
    }

    private Order order(List<OrderItem> items) {
        return Order.create(
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                shippingAddress(),
                items,
                NOW);
    }

    private Order paidOrder(List<OrderItem> items) {
        Order order = order(items);
        ReflectionTestUtils.setField(order, "status", OrderStatus.PAID);
        return order;
    }

    private static void complete(Order order, OrderItem item) {
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.PREPARING);
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.SHIPPED);
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.DELIVERED);
        order.changeItemStatus(item.getCreatorId(), item.getId(), OrderItemStatus.COMPLETED);
    }

    private OrderItem item(UUID skuId, long unitPrice, int quantity, long discount) {
        return OrderItem.create(
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                skuId,
                null,
                ProductSnapshot.of("상품", "옵션", Money.won(unitPrice), quantity),
                Money.won(discount));
    }

    private static ShippingAddress shippingAddress() {
        return ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울시 강남구", "101호");
    }

    private static void assertOrderError(Runnable action, OrderErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
