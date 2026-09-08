package com.sub9.orderservice.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.identifier.UuidV7Generator;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주문 명령 멱등 모델")
class OrderCommandRequestTest {

    private static final String REQUEST_HASH = "a".repeat(64);
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-03T10:00:00Z");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Test
    @DisplayName("멱등 키는 보이는 ASCII 문자 1자 이상 100자 이하만 허용한다")
    void when_idempotency_key_is_valid_visible_ascii_value_is_preserved() {
        assertThat(IdempotencyKey.from("A").value()).isEqualTo("A");
        assertThat(IdempotencyKey.from("a".repeat(100)).value()).hasSize(100);
        assertThat(IdempotencyKey.from("Key-01_~").value()).isEqualTo("Key-01_~");
    }

    @Test
    @DisplayName("비어 있거나 공백, 제어 문자, 비ASCII 또는 101자인 멱등 키를 거부한다")
    void when_idempotency_key_is_invalid_validation_error_is_raised() {
        assertInvalidKey(null);
        assertInvalidKey("");
        assertInvalidKey("contains space");
        assertInvalidKey("line\nbreak");
        assertInvalidKey("한글");
        assertInvalidKey("a".repeat(101));
    }

    @Test
    @DisplayName("처리 중 명령은 실패 결과로 한 번만 완료할 수 있다")
    void when_processing_command_fails_result_is_fixed_once() {
        OrderCommandRequest request = commandRequest();

        request.completeFailure(null, 409, "{\"errorCode\":\"ORDER_0007\"}", COMPLETED_AT);

        assertThat(request.getStatus()).isEqualTo(OrderCommandStatus.FAILED);
        assertThat(request.getOrderId()).isNull();
        assertThat(request.getResponseStatus()).isEqualTo((short) 409);
        assertThat(request.getResponsePayload()).contains("ORDER_0007");
        assertThat(request.getCompletedAt()).isEqualTo(COMPLETED_AT);
        assertThatThrownBy(() -> request.completeFailure(
                null, 400, "{\"errorCode\":\"COMMON_0002\"}", COMPLETED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("명령 결과와 맞지 않는 HTTP 상태 또는 SHA-256 형식을 거부한다")
    void when_command_result_is_invalid_request_is_rejected() {
        assertThatThrownBy(() -> commandRequest().completeFailure(
                null, 200, "{\"message\":\"실패\"}", COMPLETED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderCommandRequest.start(
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                OrderCommandType.CREATE_ORDER,
                IdempotencyKey.from("key"),
                "not-a-hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private OrderCommandRequest commandRequest() {
        return OrderCommandRequest.start(
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                OrderCommandType.CREATE_ORDER,
                IdempotencyKey.from("key"),
                REQUEST_HASH);
    }

    private static void assertInvalidKey(String key) {
        assertThatThrownBy(() -> IdempotencyKey.from(key))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.VALIDATION_ERROR));
    }
}
