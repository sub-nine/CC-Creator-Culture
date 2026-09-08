package com.sub9.orderservice.order.domain.model;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSnapshot {

    @Column(name = "product_name", nullable = false, updatable = false, length = 200)
    private String productName;

    @Column(name = "sku_name", nullable = false, updatable = false, length = 100)
    private String skuName;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_price", nullable = false, updatable = false))
    private Money unitPrice;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    private ProductSnapshot(String productName, String skuName, Money unitPrice, int quantity) {
        this.productName = requireText(productName, 200, "상품명");
        this.skuName = requireText(skuName, 100, "SKU명");
        this.unitPrice = Objects.requireNonNull(unitPrice, "상품 단가는 필수입니다.");
        if (quantity < 1) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }
        this.quantity = quantity;
    }

    public static ProductSnapshot of(String productName, String skuName, Money unitPrice, int quantity) {
        return new ProductSnapshot(productName, skuName, unitPrice, quantity);
    }

    Money originalAmount() {
        return unitPrice.multiply(quantity);
    }

    private static String requireText(String value, int maxLength, String fieldName) {
        Objects.requireNonNull(value, fieldName + " 값은 필수입니다.");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "의 길이가 올바르지 않습니다.");
        }
        return normalized;
    }
}
