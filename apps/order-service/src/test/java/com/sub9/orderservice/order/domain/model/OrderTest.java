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

    private Order order(List<OrderItem> items) {
        return Order.create(
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                shippingAddress(),
                items,
                NOW);
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
