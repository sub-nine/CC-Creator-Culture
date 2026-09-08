package com.sub9.common.kafka.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OrderPaidEventTest {

    @Test
    void when_serialized_product_totals_are_uuid_keyed_json_and_round_trip() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderPaidEvent event = new OrderPaidEvent(orderId, Map.of(productId, 5L));
        JsonMapper mapper = new JsonMapper();

        String json = mapper.writeValueAsString(event);

        assertThat(mapper.readTree(json).get("productQuantities").isObject()).isTrue();
        assertThat(mapper.readTree(json).get("productQuantities")
                .get(productId.toString()).asLong()).isEqualTo(5L);
        assertThat(mapper.readValue(json, OrderPaidEvent.class)).isEqualTo(event);
    }
}
