package com.sub9.common.kafka.event;

import java.util.List;
import java.util.UUID;

public record OrderPaidEvent(
        UUID orderId,
        List<ProductQuantity> productQuantities
) {
    public record ProductQuantity(UUID productId, Long quantity) {
    }
}
