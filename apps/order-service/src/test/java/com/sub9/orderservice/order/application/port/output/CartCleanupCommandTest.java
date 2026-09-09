package com.sub9.orderservice.order.application.port.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CartCleanupCommandTest {

    @Test
    @DisplayName("주문을 변환하면 사용자와 원본 장바구니 항목을 순서대로 전달한다")
    void 원본_식별자가_있을_때_변환하면_주문과_사용자와_항목을_전달한다() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Order order = order(first, second);

        CartCleanupCommand command = CartCleanupCommand.from(order).orElseThrow();

        assertThat(command.orderId()).isEqualTo(order.getId());
        assertThat(command.customerId()).isEqualTo(order.getCustomerId());
        assertThat(command.cartItemIds()).containsExactly(first, second);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("기존 주문 항목이 섞이면 원본 식별자가 있는 항목만 전달한다")
    void 원본_식별자가_일부_없을_때_변환하면_없는_항목을_제외한다() {
        UUID cartItemId = UUID.randomUUID();
        assertThat(CartCleanupCommand.from(order(null, cartItemId)).orElseThrow().cartItemIds())
                .containsExactly(cartItemId);
        assertThat(CartCleanupCommand.from(order(null, null))).isEmpty();
    }

    @Test
    @DisplayName("필수 입력이나 목록 항목이 null이면 정리 요청을 거부한다")
    void 필수_입력이_null일_때_생성하면_예외가_발생한다() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new CartCleanupCommand(null, id, List.of(id)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CartCleanupCommand(id, null, List.of(id)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CartCleanupCommand(id, id, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CartCleanupCommand(id, id, Arrays.asList(id, null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CartCleanupCommand.from(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("정리할 항목이 없거나 중복되면 요청을 거부한다")
    void 목록이_비거나_중복될_때_생성하면_예외가_발생한다() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new CartCleanupCommand(id, id, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CartCleanupCommand(id, id, List.of(id, id)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CartCleanupCommand.from(order(id, id)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("요청 생성 후 원본 목록 변경이 반영되지 않고 반환 목록은 수정할 수 없다")
    void 요청이_생성된_때_목록을_변경하면_요청의_항목은_유지된다() {
        UUID id = UUID.randomUUID();
        List<UUID> ids = new ArrayList<>(List.of(id));
        CartCleanupCommand command = new CartCleanupCommand(id, id, ids);
        ids.clear();

        assertThat(command.cartItemIds()).containsExactly(id);
        assertThatThrownBy(() -> command.cartItemIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Order order(UUID... cartItemIds) {
        UuidV7Generator ids = new UuidV7Generator();
        List<OrderItem> items = Arrays.stream(cartItemIds)
                .map(cartItemId -> OrderItem.create(ids.generate(), cartItemId,
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                        ProductSnapshot.of("상품", "옵션", Money.won(10_000), 1), Money.won(0)))
                .toList();
        return Order.create(ids.generate(), UUID.randomUUID(),
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울시 강남구", "101호"),
                items, Instant.parse("2026-09-09T00:00:00Z"));
    }
}
