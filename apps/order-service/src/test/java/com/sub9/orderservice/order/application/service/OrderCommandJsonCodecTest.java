package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.orderservice.order.domain.model.OrderCommandType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("주문 명령 JSON 처리")
class OrderCommandJsonCodecTest {

    private static final UUID ACTOR_ID = UUID.fromString("01990a60-0000-7000-8000-000000000001");

    private final JsonMapper jsonMapper = new JsonMapper();
    private final OrderCommandJsonCodec codec = new OrderCommandJsonCodec(jsonMapper);

    @Test
    @DisplayName("객체 필드 순서와 원문 공백이 달라도 같은 요청 해시를 만든다")
    void when_json_object_order_and_whitespace_differ_hash_is_same() throws Exception {
        JsonNode first = jsonMapper.readTree("{\"b\":2,\"a\":1,\"items\":[1,2]}");
        JsonNode second = jsonMapper.readTree("{ \"items\" : [ 1, 2 ], \"a\" : 1, \"b\" : 2 }");

        assertThat(codec.hash(ACTOR_ID, OrderCommandType.CREATE_ORDER, first))
                .isEqualTo(codec.hash(ACTOR_ID, OrderCommandType.CREATE_ORDER, second))
                .hasSize(64);
    }

    @Test
    @DisplayName("요청 값이나 배열 순서가 다르면 다른 요청 해시를 만든다")
    void when_json_value_or_array_order_differs_hash_is_different() throws Exception {
        JsonNode original = jsonMapper.readTree("{\"value\":1,\"items\":[1,2]}");
        JsonNode changedValue = jsonMapper.readTree("{\"value\":2,\"items\":[1,2]}");
        JsonNode changedOrder = jsonMapper.readTree("{\"value\":1,\"items\":[2,1]}");

        String originalHash = codec.hash(ACTOR_ID, OrderCommandType.CREATE_ORDER, original);

        assertThat(codec.hash(ACTOR_ID, OrderCommandType.CREATE_ORDER, changedValue))
                .isNotEqualTo(originalHash);
        assertThat(codec.hash(ACTOR_ID, OrderCommandType.CREATE_ORDER, changedOrder))
                .isNotEqualTo(originalHash);
    }

    @Test
    @DisplayName("안전한 응답은 정규화해 복원하고 민감정보 필드는 거부한다")
    void when_response_is_encoded_safe_body_is_restored_and_sensitive_body_is_rejected() {
        String encoded = codec.encodeResponse(Map.of(
                "message", "주문 생성 성공",
                "data", Map.of("orderNumber", "ORD-1", "status", "PENDING_PAYMENT")));

        assertThat(codec.decodeResponse(encoded).path("data").path("orderNumber").asString())
                .isEqualTo("ORD-1");
        assertThatThrownBy(() -> codec.encodeResponse(Map.of(
                "data", Map.of("shippingAddress", Map.of("addressLine1", "서울시")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("민감정보");
        assertThatThrownBy(() -> codec.encodeResponse(Map.of(
                "data", Map.of("accessToken", "secret-token"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("민감정보");
    }
}
