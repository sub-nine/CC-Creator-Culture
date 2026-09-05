package com.sub9.orderservice.order.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderNumber {

    private static final Pattern FORMAT = Pattern.compile("ORD-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Column(name = "order_number", nullable = false, updatable = false, length = 40)
    private String value;

    private OrderNumber(String value) {
        Objects.requireNonNull(value, "주문번호는 필수입니다.");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("주문번호는 ORD-<UUID> 형식이어야 합니다.");
        }
        this.value = value;
    }

    public static OrderNumber issue(UUID orderId) {
        return new OrderNumber("ORD-" + Objects.requireNonNull(orderId, "주문 식별자는 필수입니다."));
    }

    public static OrderNumber from(String value) {
        return new OrderNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
