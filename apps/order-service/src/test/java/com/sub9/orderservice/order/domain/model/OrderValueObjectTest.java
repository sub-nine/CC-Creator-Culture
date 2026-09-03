package com.sub9.orderservice.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주문 값 객체")
class OrderValueObjectTest {

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Test
    @DisplayName("주문 ID로 외부 노출 주문번호를 발급한다")
    void when_order_id_is_given_order_number_uses_expected_format() {
        OrderNumber orderNumber = OrderNumber.issue(uuidGenerator.generate());

        assertThat(orderNumber.getValue()).matches(
                "ORD-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(OrderNumber.from(orderNumber.getValue())).isEqualTo(orderNumber);
    }

    @Test
    @DisplayName("음수 금액과 산술 오버플로를 거부한다")
    void when_money_is_invalid_calculation_is_rejected() {
        assertOrderError(() -> Money.won(-1), OrderErrorCode.INVALID_ORDER_AMOUNT);
        assertOrderError(() -> Money.won(1).subtract(Money.won(2)), OrderErrorCode.INVALID_ORDER_AMOUNT);
        assertOrderError(() -> Money.won(Long.MAX_VALUE).add(Money.won(1)),
                OrderErrorCode.INVALID_ORDER_AMOUNT);
        assertOrderError(() -> Money.won(Long.MAX_VALUE).multiply(2),
                OrderErrorCode.INVALID_ORDER_AMOUNT);
        assertThatThrownBy(() -> Money.won(1).add(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("더할 금액은 필수입니다.");
    }

    @Test
    @DisplayName("배송지 필수값과 길이를 검증한다")
    void when_shipping_address_is_invalid_creation_is_rejected() {
        assertThatThrownBy(() -> ShippingAddress.of(" ", "010-1234-5678", "06236", "서울", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수령인 이름의 길이가 올바르지 않습니다.");
        assertThatThrownBy(() -> ShippingAddress.of("홍길동", "010-1234-5678", null, "서울", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("우편번호 값은 필수입니다.");
        assertThatThrownBy(() -> ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울",
                "가".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("상품 스냅샷은 양수 수량만 허용한다")
    void when_snapshot_quantity_is_not_positive_creation_is_rejected() {
        assertOrderError(() -> ProductSnapshot.of("상품", "옵션", Money.won(1_000), 0),
                OrderErrorCode.INVALID_ORDER_ITEMS);
    }

    private static void assertOrderError(Runnable action, OrderErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
