package com.sub9.orderservice.order.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddress {

    @Column(name = "recipient_name", nullable = false, updatable = false, length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, updatable = false, length = 20)
    private String recipientPhone;

    @Column(name = "postal_code", nullable = false, updatable = false, length = 10)
    private String postalCode;

    @Column(name = "address_line1", nullable = false, updatable = false, length = 200)
    private String addressLine1;

    @Column(name = "address_line2", updatable = false, length = 200)
    private String addressLine2;

    private ShippingAddress(String recipientName, String recipientPhone, String postalCode,
            String addressLine1, String addressLine2) {
        this.recipientName = requireText(recipientName, 50, "수령인 이름");
        this.recipientPhone = requireText(recipientPhone, 20, "수령인 연락처");
        this.postalCode = requireText(postalCode, 10, "우편번호");
        this.addressLine1 = requireText(addressLine1, 200, "기본 주소");
        this.addressLine2 = optionalText(addressLine2, 200, "상세 주소");
    }

    public static ShippingAddress of(String recipientName, String recipientPhone, String postalCode,
            String addressLine1, String addressLine2) {
        return new ShippingAddress(recipientName, recipientPhone, postalCode, addressLine1, addressLine2);
    }

    private static String requireText(String value, int maxLength, String fieldName) {
        Objects.requireNonNull(value, fieldName + " 값은 필수입니다.");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "의 길이가 올바르지 않습니다.");
        }
        return normalized;
    }

    private static String optionalText(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "의 길이가 올바르지 않습니다.");
        }
        return normalized;
    }
}
