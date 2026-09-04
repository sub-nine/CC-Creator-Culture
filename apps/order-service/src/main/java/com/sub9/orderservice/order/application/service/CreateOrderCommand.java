package com.sub9.orderservice.order.application.service;

import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
        List<Item> items,
        ShippingAddress shippingAddress
) {

    public CreateOrderCommand {
        if (items != null) {
            items = List.copyOf(items);
        }
    }

    public record Item(UUID cartItemId, UUID userCouponId) {
    }

    public record ShippingAddress(
            String recipientName,
            String recipientPhone,
            String postalCode,
            String addressLine1,
            String addressLine2
    ) {
    }
}
