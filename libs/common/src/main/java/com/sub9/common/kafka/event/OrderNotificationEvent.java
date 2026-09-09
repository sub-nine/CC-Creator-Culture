package com.sub9.common.kafka.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderNotificationEvent(
        UUID eventId,
        String eventType,
        String sourceService,
        String referenceType,
        UUID referenceId,
        UUID buyerId,
        String orderNumber,
        String paymentStatus,
        String cancellationScope,
        Instant occurredAt
) {
    public OrderNotificationEvent {
        Objects.requireNonNull(eventId, "이벤트 ID는 필수입니다.");
        Objects.requireNonNull(referenceId, "주문 ID는 필수입니다.");
        Objects.requireNonNull(buyerId, "구매자 ID는 필수입니다.");
        Objects.requireNonNull(occurredAt, "이벤트 발생 시각은 필수입니다.");
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("주문 번호는 필수입니다.");
        }
        if (!"ORDER_SERVICE".equals(sourceService) || !"ORDER".equals(referenceType)) {
            throw new IllegalArgumentException("주문 알림의 발생 서비스와 참조 유형이 올바르지 않습니다.");
        }
        boolean valid = switch (eventType == null ? "" : eventType) {
            case "PAYMENT_PAID" -> "PAID".equals(paymentStatus) && cancellationScope == null;
            case "PAYMENT_FAILED" -> ("FAILED".equals(paymentStatus) || "EXPIRED".equals(paymentStatus))
                    && cancellationScope == null;
            case "ORDER_CANCELLED" -> paymentStatus == null && "FULL".equals(cancellationScope);
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("주문 알림의 이벤트 종류와 결제 상태 또는 취소 범위가 올바르지 않습니다.");
        }
    }
}
