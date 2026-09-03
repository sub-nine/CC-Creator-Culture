package com.sub9.orderservice.order.application.port.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort.CartItemSnapshot;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.AppliedCoupon;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.CouponApplicationRequest;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주문 협업 포트 값")
class OrderCollaborationPortValueTest {

    @Test
    @DisplayName("장바구니 스냅샷의 필수값과 수량을 검증한다")
    void when_cart_snapshot_is_created_required_values_are_validated() {
        CartItemSnapshot snapshot = new CartItemSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                " 상품 ",
                " 옵션 ",
                10_000,
                2);

        assertThat(snapshot.productName()).isEqualTo("상품");
        assertThat(snapshot.skuName()).isEqualTo("옵션");
        assertThatThrownBy(() -> new CartItemSnapshot(
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "상품",
                "옵션",
                10_000,
                1))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(OrderErrorCode.INVALID_ORDER_ITEMS));
        assertThatThrownBy(() -> new CartItemSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "상품",
                "옵션",
                -1,
                1))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(OrderErrorCode.INVALID_ORDER_AMOUNT));
        assertThatThrownBy(() -> new CartItemSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "상품",
                "옵션",
                10_000,
                0))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(OrderErrorCode.INVALID_ORDER_ITEMS));
    }

    @Test
    @DisplayName("쿠폰 적용 요청과 결과는 식별자와 음수 금액을 거부한다")
    void when_coupon_values_are_created_identifiers_and_amounts_are_validated() {
        assertThatThrownBy(() -> new CouponApplicationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                10_000))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(OrderErrorCode.INVALID_ORDER_ITEMS));
        assertThatThrownBy(() -> new CouponApplicationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                -1))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(OrderErrorCode.INVALID_ORDER_AMOUNT));
        assertThatThrownBy(() -> new AppliedCoupon(
                UUID.randomUUID(),
                UUID.randomUUID(),
                -1))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(OrderErrorCode.INVALID_ORDER_AMOUNT));
    }

    @Test
    @DisplayName("재고 항목은 SKU와 양수 수량만 허용한다")
    void when_stock_item_is_created_sku_and_quantity_are_validated() {
        assertThatThrownBy(() -> new StockItem(null, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(OrderErrorCode.INVALID_ORDER_ITEMS));
        assertThatThrownBy(() -> new StockItem(UUID.randomUUID(), 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(OrderErrorCode.INVALID_ORDER_ITEMS));
    }

    @Test
    @DisplayName("재고 처리 결과 불명 예외는 원인을 보존한다")
    void when_stock_result_is_uncertain_cause_is_preserved() {
        RuntimeException cause = new RuntimeException("연결 종료");

        StockOperationUncertainException exception =
                new StockOperationUncertainException("재고 차감 결과를 확인할 수 없습니다.", cause);

        assertThat(exception).hasMessage("재고 차감 결과를 확인할 수 없습니다.");
        assertThat(exception).hasCause(cause);
    }
}
