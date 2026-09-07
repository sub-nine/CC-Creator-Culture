package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.common.dto.response.ErrorResponse;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.exception.ErrorCode;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort.CartItemSnapshot;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.AppliedCoupon;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.CouponApplicationRequest;
import com.sub9.orderservice.order.application.port.output.StockOperationUncertainException;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderCommandType;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderItemStatus;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.presentation.response.CreateOrderResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

@ExtendWith(MockitoExtension.class)
@DisplayName("주문 생성 서비스")
class OrderCreationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
    private static final String IDEMPOTENCY_KEY = "create-order-key";
    private static final UUID COMMAND_REQUEST_ID = uuid(1);
    private static final UUID CUSTOMER_ID = uuid(2);
    private static final UUID CART_ITEM_ID_1 = uuid(3);
    private static final UUID CART_ITEM_ID_2 = uuid(4);
    private static final UUID CREATOR_ID_1 = uuid(5);
    private static final UUID CREATOR_ID_2 = uuid(6);
    private static final UUID PRODUCT_ID_1 = uuid(7);
    private static final UUID PRODUCT_ID_2 = uuid(8);
    private static final UUID SKU_ID_1 = uuid(9);
    private static final UUID SKU_ID_2 = uuid(10);
    private static final UUID USER_COUPON_ID = uuid(11);

    private static final CartItemSnapshot SNAPSHOT_1 = new CartItemSnapshot(
            CART_ITEM_ID_1, CREATOR_ID_1, PRODUCT_ID_1, SKU_ID_1,
            "상품 1", "옵션 1", 10_000, 2);
    private static final CartItemSnapshot SNAPSHOT_2 = new CartItemSnapshot(
            CART_ITEM_ID_2, CREATOR_ID_2, PRODUCT_ID_2, SKU_ID_2,
            "상품 2", "옵션 2", 8_000, 1);
    private static final CouponApplicationRequest COUPON_REQUEST = new CouponApplicationRequest(
            CART_ITEM_ID_2, PRODUCT_ID_2, SKU_ID_2, USER_COUPON_ID, 8_000);
    private static final AppliedCoupon APPLIED_COUPON =
            new AppliedCoupon(CART_ITEM_ID_2, USER_COUPON_ID, 3_000);

    @Mock
    private OrderCommandIdempotencyService idempotencyService;
    @Mock
    private CartSnapshotPort cartSnapshotPort;
    @Mock
    private CouponApplicationPort couponApplicationPort;
    @Mock
    private StockPort stockPort;
    @Mock
    private OrderCreationTransactionService transactionService;
    @Captor
    private ArgumentCaptor<Order> orderCaptor;
    @Captor
    private ArgumentCaptor<List<AppliedCoupon>> couponListCaptor;
    @Captor
    private ArgumentCaptor<ApiResponse<CreateOrderResponse>> responseCaptor;

    private OrderCreationService service;

    @BeforeEach
    void setUp() {
        service = new OrderCreationService(
                idempotencyService,
                cartSnapshotPort,
                couponApplicationPort,
                stockPort,
                transactionService,
                new UuidV7Generator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("여러 창작자의 상품과 쿠폰을 함께 주문하고 계산된 금액을 저장한다")
    void when_multiple_creator_items_and_coupon_are_valid_order_is_created_in_sequence() {
        CreateOrderCommand command = orderCommand(
                new CreateOrderCommand.Item(CART_ITEM_ID_1, null),
                new CreateOrderCommand.Item(CART_ITEM_ID_2, USER_COUPON_ID));
        List<CartItemSnapshot> snapshots = List.of(SNAPSHOT_1, SNAPSHOT_2);
        List<UUID> cartItemIds = List.of(CART_ITEM_ID_1, CART_ITEM_ID_2);
        List<StockItem> stockItems = List.of(
                new StockItem(SKU_ID_1, 2),
                new StockItem(SKU_ID_2, 1));
        stubStarted(command);
        when(cartSnapshotPort.getCartItems(CUSTOMER_ID, cartItemIds)).thenReturn(snapshots);
        when(couponApplicationPort.apply(CUSTOMER_ID, List.of(COUPON_REQUEST)))
                .thenReturn(List.of(APPLIED_COUPON));

        OrderCreationResult result = service.create(CUSTOMER_ID, IDEMPOTENCY_KEY, command);

        verify(transactionService).save(
                eq(COMMAND_REQUEST_ID),
                orderCaptor.capture(),
                couponListCaptor.capture(),
                responseCaptor.capture());
        Order order = orderCaptor.getValue();
        ApiResponse<CreateOrderResponse> responseBody = responseCaptor.getValue();
        CreateOrderResponse response = responseBody.getData();

        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.responseBody()).isSameAs(responseBody);
        assertThat(responseBody.getMessage()).isEqualTo("주문 생성 성공");
        assertThat(response.originalAmount()).isEqualTo(28_000);
        assertThat(response.discountAmount()).isEqualTo(3_000);
        assertThat(response.paymentAmount()).isEqualTo(25_000);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(order.getOriginalAmount().getAmount()).isEqualTo(28_000);
        assertThat(order.getDiscountAmount().getAmount()).isEqualTo(3_000);
        assertThat(order.getPaymentAmount().getAmount()).isEqualTo(25_000);
        assertThat(order.getItems())
                .extracting(OrderItem::getCreatorId)
                .containsExactly(CREATOR_ID_1, CREATOR_ID_2);
        assertThat(order.getItems())
                .extracting(OrderItem::getStatus)
                .containsOnly(OrderItemStatus.ORDERED);
        assertThat(order.getItems().get(0).getUserCouponId()).isNull();
        assertThat(order.getItems().get(1).getUserCouponId()).isEqualTo(USER_COUPON_ID);
        assertThat(couponListCaptor.getValue()).containsExactly(APPLIED_COUPON);

        InOrder calls = inOrder(
                idempotencyService,
                cartSnapshotPort,
                couponApplicationPort,
                stockPort,
                transactionService);
        calls.verify(idempotencyService).acquire(
                CUSTOMER_ID, OrderCommandType.CREATE_ORDER, IDEMPOTENCY_KEY, command);
        calls.verify(cartSnapshotPort).getCartItems(CUSTOMER_ID, cartItemIds);
        calls.verify(couponApplicationPort).apply(CUSTOMER_ID, List.of(COUPON_REQUEST));
        calls.verify(stockPort).deduct(order.getId(), stockItems);
        calls.verify(transactionService).save(
                eq(COMMAND_REQUEST_ID), same(order), eq(List.of(APPLIED_COUPON)), same(responseBody));
    }

    @Test
    @DisplayName("완료된 멱등 요청은 저장된 응답을 반환하고 외부 포트를 호출하지 않는다")
    void when_completed_request_is_replayed_external_ports_are_not_called() {
        CreateOrderCommand command = orderCommand(
                new CreateOrderCommand.Item(CART_ITEM_ID_1, null));
        JsonNode replayBody = org.mockito.Mockito.mock(JsonNode.class);
        when(idempotencyService.acquire(
                CUSTOMER_ID, OrderCommandType.CREATE_ORDER, IDEMPOTENCY_KEY, command))
                .thenReturn(new OrderCommandAcquireResult.Replay(201, replayBody));

        OrderCreationResult result = service.create(CUSTOMER_ID, IDEMPOTENCY_KEY, command);

        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.responseBody()).isSameAs(replayBody);
        verifyNoInteractions(cartSnapshotPort, couponApplicationPort, stockPort, transactionService);
    }

    @Test
    @DisplayName("중복 장바구니 항목은 ORDER_0009 실패로 기록한다")
    void when_cart_item_is_duplicated_invalid_items_failure_is_recorded() {
        CreateOrderCommand command = orderCommand(
                new CreateOrderCommand.Item(CART_ITEM_ID_1, null),
                new CreateOrderCommand.Item(CART_ITEM_ID_1, null));
        stubStarted(command);

        assertBusinessError(
                () -> service.create(CUSTOMER_ID, IDEMPOTENCY_KEY, command),
                OrderErrorCode.INVALID_ORDER_ITEMS);

        verifyFailureRecorded(OrderErrorCode.INVALID_ORDER_ITEMS);
        verifyNoInteractions(cartSnapshotPort, couponApplicationPort, stockPort, transactionService);
    }

    @ParameterizedTest
    @MethodSource("invalidCartSnapshots")
    @DisplayName("장바구니 응답의 항목이 누락되거나 중복되거나 추가되면 ORDER_0009로 기록한다")
    void when_cart_snapshot_items_do_not_match_request_invalid_items_failure_is_recorded(
            List<CartItemSnapshot> invalidSnapshots) {
        CreateOrderCommand command = orderCommand(
                new CreateOrderCommand.Item(CART_ITEM_ID_1, null),
                new CreateOrderCommand.Item(CART_ITEM_ID_2, null));
        stubStarted(command);
        when(cartSnapshotPort.getCartItems(
                CUSTOMER_ID, List.of(CART_ITEM_ID_1, CART_ITEM_ID_2)))
                .thenReturn(invalidSnapshots);

        assertBusinessError(
                () -> service.create(CUSTOMER_ID, IDEMPOTENCY_KEY, command),
                OrderErrorCode.INVALID_ORDER_ITEMS);

        verifyFailureRecorded(OrderErrorCode.INVALID_ORDER_ITEMS);
        verifyNoInteractions(couponApplicationPort, stockPort, transactionService);
    }

    @Test
    @DisplayName("쿠폰 응답이 요청한 쿠폰과 다르면 ORDER_0009 실패로 기록한다")
    void when_coupon_result_does_not_match_request_invalid_items_failure_is_recorded() {
        CreateOrderCommand command = orderCommand(
                new CreateOrderCommand.Item(CART_ITEM_ID_2, USER_COUPON_ID));
        stubStarted(command);
        when(cartSnapshotPort.getCartItems(CUSTOMER_ID, List.of(CART_ITEM_ID_2)))
                .thenReturn(List.of(SNAPSHOT_2));
        when(couponApplicationPort.apply(CUSTOMER_ID, List.of(COUPON_REQUEST)))
                .thenReturn(List.of(new AppliedCoupon(
                        CART_ITEM_ID_2,
                        UUID.randomUUID(),
                        3_000)));

        assertBusinessError(
                () -> service.create(CUSTOMER_ID, IDEMPOTENCY_KEY, command),
                OrderErrorCode.INVALID_ORDER_ITEMS);

        verifyFailureRecorded(OrderErrorCode.INVALID_ORDER_ITEMS);
        verifyNoInteractions(stockPort, transactionService);
    }

    @Test
    @DisplayName("할인액이 상품 금액을 넘으면 ORDER_0010 실패로 기록한다")
    void when_coupon_discount_exceeds_original_amount_invalid_amount_failure_is_recorded() {
        CreateOrderCommand command = orderCommand(
                new CreateOrderCommand.Item(CART_ITEM_ID_2, USER_COUPON_ID));
        stubStarted(command);
        when(cartSnapshotPort.getCartItems(CUSTOMER_ID, List.of(CART_ITEM_ID_2)))
                .thenReturn(List.of(SNAPSHOT_2));
        when(couponApplicationPort.apply(CUSTOMER_ID, List.of(COUPON_REQUEST)))
                .thenReturn(List.of(new AppliedCoupon(CART_ITEM_ID_2, USER_COUPON_ID, 8_001)));

        assertBusinessError(
                () -> service.create(CUSTOMER_ID, IDEMPOTENCY_KEY, command),
                OrderErrorCode.INVALID_ORDER_AMOUNT);

        verifyFailureRecorded(OrderErrorCode.INVALID_ORDER_AMOUNT);
        verifyNoInteractions(stockPort, transactionService);
    }

    @Test
    @DisplayName("재고 차감의 확정된 비즈니스 실패를 원래 오류로 기록한다")
    void when_stock_deduction_has_business_failure_original_failure_is_recorded() {
        CreateOrderCommand command = singleItemCommand();
        stubStartedWithCart(command);
        BusinessException stockFailure = new BusinessException(TestStockError.INSUFFICIENT_STOCK);
        doThrow(stockFailure).when(stockPort).deduct(any(UUID.class), eq(List.of(stockItem())));

        assertBusinessError(
                () -> service.create(CUSTOMER_ID, IDEMPOTENCY_KEY, command),
                TestStockError.INSUFFICIENT_STOCK);

        verifyFailureRecorded(TestStockError.INSUFFICIENT_STOCK);
        verify(stockPort, never()).restore(any(), any(), any());
        verifyNoInteractions(couponApplicationPort, transactionService);
    }

    @Test
    @DisplayName("재고 차감 결과가 불명확하면 실패를 확정하지 않는다")
    void when_stock_deduction_is_uncertain_failure_record_is_not_completed() {
        CreateOrderCommand command = singleItemCommand();
        stubStartedWithCart(command);
        StockOperationUncertainException uncertain = new StockOperationUncertainException(
                "재고 차감 결과를 확인할 수 없습니다.");
        doThrow(uncertain).when(stockPort).deduct(any(UUID.class), eq(List.of(stockItem())));

        assertThatThrownBy(() -> service.create(CUSTOMER_ID, IDEMPOTENCY_KEY, command))
                .isSameAs(uncertain);

        verify(stockPort, never()).restore(any(), any(), any());
        verify(idempotencyService, never()).completeFailure(any(), any(), anyInt(), any());
        verifyNoInteractions(couponApplicationPort, transactionService);
    }

    @Test
    @DisplayName("트랜잭션 저장 실패는 재고를 복구한 뒤 500 실패로 기록한다")
    void when_transaction_save_fails_stock_is_restored_before_internal_failure_is_recorded() {
        CreateOrderCommand command = singleItemCommand();
        stubStartedWithCart(command);
        RuntimeException saveFailure = new IllegalStateException("주문 저장 실패");
        doThrow(saveFailure).when(transactionService).save(
                eq(COMMAND_REQUEST_ID), any(Order.class), eq(List.of()), any());

        assertThatThrownBy(() -> service.create(CUSTOMER_ID, IDEMPOTENCY_KEY, command))
                .isSameAs(saveFailure);

        verify(transactionService).save(
                eq(COMMAND_REQUEST_ID), orderCaptor.capture(), eq(List.of()), any());
        Order order = orderCaptor.getValue();
        InOrder calls = inOrder(stockPort, transactionService, idempotencyService);
        calls.verify(stockPort).deduct(order.getId(), List.of(stockItem()));
        calls.verify(transactionService).save(
                eq(COMMAND_REQUEST_ID), same(order), eq(List.of()), any());
        calls.verify(stockPort).restore(
                order.getId(), List.of(stockItem()), RestoreReason.ORDER_CREATION_FAILED);
        calls.verify(idempotencyService).completeFailure(
                eq(COMMAND_REQUEST_ID),
                isNull(),
                eq(HttpStatus.INTERNAL_SERVER_ERROR.value()),
                any());
        verifyFailureRecorded(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("재고 복구 결과가 불명확하면 실패를 확정하지 않는다")
    void when_stock_restore_is_uncertain_failure_record_is_not_completed() {
        CreateOrderCommand command = singleItemCommand();
        stubStartedWithCart(command);
        RuntimeException saveFailure = new IllegalStateException("주문 저장 실패");
        RuntimeException restoreFailure = new IllegalStateException("재고 복구 응답 없음");
        doThrow(saveFailure).when(transactionService).save(
                eq(COMMAND_REQUEST_ID), any(Order.class), eq(List.of()), any());
        doThrow(restoreFailure).when(stockPort).restore(
                any(UUID.class), eq(List.of(stockItem())), eq(RestoreReason.ORDER_CREATION_FAILED));

        assertThatThrownBy(() -> service.create(CUSTOMER_ID, IDEMPOTENCY_KEY, command))
                .isInstanceOfSatisfying(StockOperationUncertainException.class, exception -> {
                    assertThat(exception).hasCause(restoreFailure);
                    assertThat(exception.getSuppressed()).containsExactly(saveFailure);
                });

        verify(stockPort).restore(
                any(UUID.class), eq(List.of(stockItem())), eq(RestoreReason.ORDER_CREATION_FAILED));
        verify(idempotencyService, never()).completeFailure(any(), any(), anyInt(), any());
    }

    private void stubStarted(CreateOrderCommand command) {
        when(idempotencyService.acquire(
                CUSTOMER_ID, OrderCommandType.CREATE_ORDER, IDEMPOTENCY_KEY, command))
                .thenReturn(new OrderCommandAcquireResult.Started(COMMAND_REQUEST_ID));
    }

    private void stubStartedWithCart(CreateOrderCommand command) {
        stubStarted(command);
        when(cartSnapshotPort.getCartItems(CUSTOMER_ID, List.of(CART_ITEM_ID_1)))
                .thenReturn(List.of(SNAPSHOT_1));
    }

    private void verifyFailureRecorded(ErrorCode errorCode) {
        ArgumentCaptor<Object> failureBodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(idempotencyService).completeFailure(
                eq(COMMAND_REQUEST_ID),
                isNull(),
                eq(errorCode.status().value()),
                failureBodyCaptor.capture());
        assertThat(failureBodyCaptor.getValue())
                .isInstanceOfSatisfying(ErrorResponse.class, response -> {
                    assertThat(response.getErrorCode()).isEqualTo(errorCode.code());
                    assertThat(response.getMessage()).isEqualTo(errorCode.message());
                    assertThat(response.getErrors()).isEmpty();
                });
    }

    private static void assertBusinessError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isSameAs(errorCode));
    }

    private static CreateOrderCommand singleItemCommand() {
        return orderCommand(new CreateOrderCommand.Item(CART_ITEM_ID_1, null));
    }

    private static CreateOrderCommand orderCommand(CreateOrderCommand.Item... items) {
        return new CreateOrderCommand(
                List.of(items),
                new CreateOrderCommand.ShippingAddress(
                        "홍길동", "010-1234-5678", "06236", "서울시 강남구", "101호"));
    }

    private static StockItem stockItem() {
        return new StockItem(SKU_ID_1, 2);
    }

    private static Stream<List<CartItemSnapshot>> invalidCartSnapshots() {
        CartItemSnapshot unexpected = new CartItemSnapshot(
                UUID.randomUUID(),
                CREATOR_ID_2,
                PRODUCT_ID_2,
                SKU_ID_2,
                "추가 상품",
                "추가 옵션",
                8_000,
                1);
        return Stream.of(
                List.of(SNAPSHOT_1),
                List.of(SNAPSHOT_1, SNAPSHOT_1),
                List.of(SNAPSHOT_1, unexpected));
    }

    private static UUID uuid(long value) {
        return new UUID(0x0199000000007000L, 0x8000000000000000L + value);
    }

    private enum TestStockError implements ErrorCode {
        INSUFFICIENT_STOCK;

        @Override
        public String code() {
            return "PRODUCT_0001";
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.BAD_REQUEST;
        }

        @Override
        public String message() {
            return "주문 가능한 재고가 부족합니다.";
        }
    }
}
