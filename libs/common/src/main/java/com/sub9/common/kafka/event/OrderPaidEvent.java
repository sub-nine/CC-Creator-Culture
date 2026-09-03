package com.sub9.common.kafka.event;

import java.util.UUID;

public record OrderPaidEvent(
        UUID orderId,
        UUID productId
) {
}
