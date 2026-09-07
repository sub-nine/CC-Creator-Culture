package com.sub9.orderservice.order.application.service;

import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public sealed interface OrderCommandAcquireResult {

    record Started(UUID commandRequestId) implements OrderCommandAcquireResult {

        public Started {
            Objects.requireNonNull(commandRequestId, "주문 명령 식별자는 필수입니다.");
        }
    }

    record Replay(int httpStatus, JsonNode responseBody) implements OrderCommandAcquireResult {

        public Replay {
            if (httpStatus < 100 || httpStatus > 599) {
                throw new IllegalArgumentException("HTTP 상태가 올바르지 않습니다.");
            }
            Objects.requireNonNull(responseBody, "재응답할 응답 본문은 필수입니다.");
        }
    }
}
