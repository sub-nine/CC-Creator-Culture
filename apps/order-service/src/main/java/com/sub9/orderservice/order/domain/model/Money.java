package com.sub9.orderservice.order.domain.model;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
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
public class Money {

    private long amount;

    private Money(long amount) {
        if (amount < 0) {
            throw invalidAmount();
        }
        this.amount = amount;
    }

    public static Money won(long amount) {
        return new Money(amount);
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "더할 금액은 필수입니다.");
        try {
            return won(Math.addExact(amount, other.amount));
        } catch (ArithmeticException exception) {
            throw invalidAmount();
        }
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "뺄 금액은 필수입니다.");
        try {
            return won(Math.subtractExact(amount, other.amount));
        } catch (ArithmeticException exception) {
            throw invalidAmount();
        }
    }

    public Money multiply(int multiplier) {
        try {
            return won(Math.multiplyExact(amount, multiplier));
        } catch (ArithmeticException exception) {
            throw invalidAmount();
        }
    }

    private static BusinessException invalidAmount() {
        return new BusinessException(OrderErrorCode.INVALID_ORDER_AMOUNT);
    }
}
