package com.sub9.common.kafka.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserDeletedEvent(
        UUID eventId,
        UUID userId,
        Instant occurredAt
) {
    public UserDeletedEvent {
        Objects.requireNonNull(eventId, "이벤트 ID는 필수입니다.");
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(occurredAt, "이벤트 발생 시각은 필수입니다.");
    }
}
