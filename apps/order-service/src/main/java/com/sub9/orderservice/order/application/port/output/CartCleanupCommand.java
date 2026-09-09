package com.sub9.orderservice.order.application.port.output;

import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CartCleanupCommand(UUID orderId, UUID customerId, List<UUID> cartItemIds) {

    public CartCleanupCommand {
        Objects.requireNonNull(orderId, "주문 식별자는 필수입니다.");
        Objects.requireNonNull(customerId, "사용자 식별자는 필수입니다.");
        cartItemIds = List.copyOf(Objects.requireNonNull(cartItemIds, "장바구니 항목 목록은 필수입니다."));
        if (cartItemIds.isEmpty()) {
            throw new IllegalArgumentException("정리할 장바구니 항목은 한 개 이상이어야 합니다.");
        }
        if (new HashSet<>(cartItemIds).size() != cartItemIds.size()) {
            throw new IllegalArgumentException("정리할 장바구니 항목이 중복되었습니다.");
        }
    }

    public static Optional<CartCleanupCommand> from(Order order) {
        Objects.requireNonNull(order, "주문은 필수입니다.");
        // 기존 주문에는 원본 식별자가 없으므로 현재 장바구니 항목을 추정하지 않는다.
        List<UUID> cartItemIds = order.getItems().stream()
                .map(OrderItem::getCartItemId)
                .filter(Objects::nonNull)
                .toList();
        return cartItemIds.isEmpty() ? Optional.empty()
                : Optional.of(new CartCleanupCommand(order.getId(), order.getCustomerId(), cartItemIds));
    }
}
