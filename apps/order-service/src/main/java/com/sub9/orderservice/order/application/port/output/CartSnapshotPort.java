package com.sub9.orderservice.order.application.port.output;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import java.util.List;
import java.util.UUID;

public interface CartSnapshotPort {

    List<CartItemSnapshot> getCartItems(UUID customerId, List<UUID> cartItemIds);

    record CartItemSnapshot(
            UUID cartItemId,
            UUID creatorId,
            UUID productId,
            UUID skuId,
            String productName,
            String skuName,
            long unitPrice,
            int quantity
        ) {

        public CartItemSnapshot {
            requireIdentifier(cartItemId);
            requireIdentifier(creatorId);
            requireIdentifier(productId);
            requireIdentifier(skuId);
            productName = requireText(productName);
            skuName = requireText(skuName);
            if (unitPrice < 0) {
                throw new BusinessException(OrderErrorCode.INVALID_ORDER_AMOUNT);
            }
            if (quantity < 1) {
                throw invalidItems();
            }
        }

        private static void requireIdentifier(UUID value) {
            if (value == null) {
                throw invalidItems();
            }
        }

        private static String requireText(String value) {
            if (value == null) {
                throw invalidItems();
            }
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                throw invalidItems();
            }
            return normalized;
        }

        private static BusinessException invalidItems() {
            return new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }
    }
}
