package com.sub9.common.kafka.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("주문 결제 완료 이벤트 JSON 변환")
class OrderPaidEventTest {

    @Test
    @DisplayName("상품별 총수량을 UUID 키의 JSON 객체로 변환하고 원래 이벤트로 복원한다")
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
