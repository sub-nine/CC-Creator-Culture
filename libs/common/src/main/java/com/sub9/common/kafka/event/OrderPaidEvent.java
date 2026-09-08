package com.sub9.common.kafka.event;

import java.util.Map;
import java.util.UUID;

public record OrderPaidEvent(
        UUID orderId,
        Map<UUID, Long> productQuantities
) {
}
