package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderQueryRepository;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorGroup;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("주문 조회 서비스")
class OrderQueryServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-04T00:00:00Z");
    private static final UUID CUSTOMER_ID = uuid(1);
    private static final UUID OTHER_CUSTOMER_ID = uuid(2);
    private static final UUID CREATOR_ID = uuid(3);
    private static final UUID OTHER_CREATOR_ID = uuid(4);

    @Mock
    private OrderQueryRepository orderQueryRepository;

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Test
    @DisplayName("소비자 주문 목록을 응답 형식으로 변환하며 페이지 정보를 유지한다")
    void when_customer_orders_are_queried_page_is_mapped_without_losing_metadata() {
        Pageable pageable = PageRequest.of(0, 20);
        Order order = order(10, CUSTOMER_ID, OrderStatus.PENDING_PAYMENT, item(11, CREATOR_ID));
        when(orderQueryRepository.findAllByCustomerId(CUSTOMER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 21));

        var result = orderQueryService.getCustomerOrders(CUSTOMER_ID, pageable);

        assertThat(result.getTotalElements()).isEqualTo(21);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getPageable()).isEqualTo(pageable);
        assertThat(result.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.orderNumber()).isEqualTo(order.getOrderNumber().toString());
            assertThat(summary.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(summary.originalAmount()).isEqualTo(20_000L);
            assertThat(summary.discountAmount()).isEqualTo(1_000L);
            assertThat(summary.paymentAmount()).isEqualTo(19_000L);
            assertThat(summary.createdAt()).isEqualTo(CREATED_AT);
            assertThat(summary.expiresAt()).isEqualTo(CREATED_AT.plusSeconds(600));
        });
        verify(orderQueryRepository).findAllByCustomerId(CUSTOMER_ID, pageable);
    }

    @Test
    @DisplayName("창작자 주문 상품 목록을 배송지 없는 응답 형식으로 변환한다")
    void when_creator_order_items_are_queried_items_are_mapped() {
        Pageable pageable = PageRequest.of(0, 20);
        OrderItem item = item(21, CREATOR_ID);
        Order order = order(20, CUSTOMER_ID, OrderStatus.PAID, item);
        when(orderQueryRepository.findAllItemsByCreatorId(CREATOR_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));

        var result = orderQueryService.getCreatorOrderItems(CREATOR_ID, pageable);

        assertThat(result.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.orderItemId()).isEqualTo(item.getId());
            assertThat(summary.orderNumber()).isEqualTo(order.getOrderNumber().toString());
            assertThat(summary.orderStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(summary.productName()).isEqualTo("아크릴 스탠드 21");
            assertThat(summary.skuName()).isEqualTo("A 타입");
            assertThat(summary.quantity()).isEqualTo(2);
            assertThat(summary.paymentAmount()).isEqualTo(19_000L);
            assertThat(summary.createdAt()).isEqualTo(CREATED_AT.plusSeconds(21));
        });
        verify(orderQueryRepository).findAllItemsByCreatorId(CREATOR_ID, pageable);
    }

    @Test
    @DisplayName("운영자 주문 목록에 소비자 식별자를 포함하며 페이지 정보를 유지한다")
    void when_admin_orders_are_queried_customer_id_is_included() {
        Pageable pageable = PageRequest.of(1, 10);
        Order order = order(30, CUSTOMER_ID, OrderStatus.FAILED, item(31, CREATOR_ID));
        when(orderQueryRepository.findAllOrders(pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 11));

        var result = orderQueryService.getAdminOrders(pageable);

        assertThat(result.getTotalElements()).isEqualTo(11);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(summary.orderNumber()).isEqualTo(order.getOrderNumber().toString());
            assertThat(summary.status()).isEqualTo(OrderStatus.FAILED);
        });
        verify(orderQueryRepository).findAllOrders(pageable);
    }

    @Test
    @DisplayName("소비자 주문 상세는 배송지와 주문 상품을 창작자별로 묶어 반환한다")
    void when_customer_order_detail_is_queried_items_are_grouped_by_creator() {
        OrderItem firstCreatorItem = item(41, CREATOR_ID);
        OrderItem otherCreatorItem = item(42, OTHER_CREATOR_ID);
        OrderItem secondCreatorItem = item(43, CREATOR_ID);
        Order order = order(
                40,
                CUSTOMER_ID,
                OrderStatus.PROCESSING,
                firstCreatorItem,
                otherCreatorItem,
                secondCreatorItem);
        when(orderQueryRepository.findDetailByOrderNumber(order.getOrderNumber()))
                .thenReturn(Optional.of(order));

        var result = orderQueryService.getCustomerOrder(CUSTOMER_ID, order.getOrderNumber());
        Map<UUID, CreatorGroup> groups = result.creatorGroups().stream()
                .collect(Collectors.toMap(CreatorGroup::creatorId, Function.identity()));

        assertThat(result.shippingAddress().recipientName()).isEqualTo("홍길동");
        assertThat(result.shippingAddress().recipientPhone()).isEqualTo("010-1234-5678");
        assertThat(groups).containsOnlyKeys(CREATOR_ID, OTHER_CREATOR_ID);
        assertThat(groups.get(CREATOR_ID).items())
                .extracting(item -> item.orderItemId())
                .containsExactlyInAnyOrder(firstCreatorItem.getId(), secondCreatorItem.getId());
        assertThat(groups.get(OTHER_CREATOR_ID).items())
                .extracting(item -> item.orderItemId())
                .containsExactly(otherCreatorItem.getId());
    }

    @Test
    @DisplayName("소비자가 타인 주문 상세를 조회하면 접근을 거부한다")
    void when_customer_queries_another_customer_order_access_is_denied() {
        Order order = order(50, OTHER_CUSTOMER_ID, OrderStatus.PAID, item(51, CREATOR_ID));
        when(orderQueryRepository.findDetailByOrderNumber(order.getOrderNumber()))
                .thenReturn(Optional.of(order));

        assertError(
                () -> orderQueryService.getCustomerOrder(CUSTOMER_ID, order.getOrderNumber()),
                OrderErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("존재하지 않는 소비자 주문 상세를 조회하면 찾을 수 없음 오류를 반환한다")
    void when_customer_order_does_not_exist_not_found_is_returned() {
        OrderNumber orderNumber = OrderNumber.from("ORD-0198f2a0-76c0-7000-8000-000000000060");
        when(orderQueryRepository.findDetailByOrderNumber(orderNumber)).thenReturn(Optional.empty());

        assertError(
                () -> orderQueryService.getCustomerOrder(CUSTOMER_ID, orderNumber),
                OrderErrorCode.ORDER_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "PROCESSING", "COMPLETED", "CANCELED"})
    @DisplayName("창작자는 본인에게 배정된 결제 이후 주문 상품 상세를 조회한다")
    void when_creator_item_is_owned_and_visible_detail_is_returned(OrderStatus status) {
        OrderItem item = item(71, CREATOR_ID);
        Order order = order(70, CUSTOMER_ID, status, item);
        when(orderQueryRepository.findItemDetailById(item.getId())).thenReturn(Optional.of(item));

        var result = orderQueryService.getCreatorOrderItem(CREATOR_ID, item.getId());

        assertThat(result.orderItemId()).isEqualTo(item.getId());
        assertThat(result.orderNumber()).isEqualTo(order.getOrderNumber().toString());
        assertThat(result.orderStatus()).isEqualTo(status);
        assertThat(result.productId()).isEqualTo(item.getProductId());
        assertThat(result.skuId()).isEqualTo(item.getSkuId());
        assertThat(result.originalAmount()).isEqualTo(20_000L);
        assertThat(result.discountAmount()).isEqualTo(1_000L);
        assertThat(result.paymentAmount()).isEqualTo(19_000L);
    }

    @Test
    @DisplayName("창작자가 타인에게 배정된 주문 상품 상세를 조회하면 접근을 거부한다")
    void when_creator_queries_another_creator_item_access_is_denied() {
        OrderItem item = item(81, OTHER_CREATOR_ID);
        order(80, CUSTOMER_ID, OrderStatus.PAID, item);
        when(orderQueryRepository.findItemDetailById(item.getId())).thenReturn(Optional.of(item));

        assertError(
                () -> orderQueryService.getCreatorOrderItem(CREATOR_ID, item.getId()),
                OrderErrorCode.ORDER_ACCESS_DENIED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING_PAYMENT", "FAILED", "EXPIRED"})
    @DisplayName("창작자 본인 상품이어도 결제 이후 주문이 아니면 상세 조회를 거부한다")
    void when_creator_item_parent_order_is_not_visible_access_is_denied(OrderStatus status) {
        OrderItem item = item(91, CREATOR_ID);
        order(90, CUSTOMER_ID, status, item);
        when(orderQueryRepository.findItemDetailById(item.getId())).thenReturn(Optional.of(item));

        assertError(
                () -> orderQueryService.getCreatorOrderItem(CREATOR_ID, item.getId()),
                OrderErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("존재하지 않는 창작자 주문 상품 상세를 조회하면 찾을 수 없음 오류를 반환한다")
    void when_creator_order_item_does_not_exist_not_found_is_returned() {
        UUID orderItemId = uuid(100);
        when(orderQueryRepository.findItemDetailById(orderItemId)).thenReturn(Optional.empty());

        assertError(
                () -> orderQueryService.getCreatorOrderItem(CREATOR_ID, orderItemId),
                OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("운영자 주문 상세는 소비자 소유권과 관계없이 창작자 그룹을 반환한다")
    void when_admin_order_detail_is_queried_customer_and_creator_groups_are_returned() {
        OrderItem item = item(111, CREATOR_ID);
        Order order = order(110, OTHER_CUSTOMER_ID, OrderStatus.COMPLETED, item);
        when(orderQueryRepository.findDetailByOrderNumber(order.getOrderNumber()))
                .thenReturn(Optional.of(order));

        var result = orderQueryService.getAdminOrder(order.getOrderNumber());

        assertThat(result.customerId()).isEqualTo(OTHER_CUSTOMER_ID);
        assertThat(result.orderNumber()).isEqualTo(order.getOrderNumber().toString());
        assertThat(result.creatorGroups()).singleElement().satisfies(group -> {
            assertThat(group.creatorId()).isEqualTo(CREATOR_ID);
            assertThat(group.items()).singleElement().satisfies(detail ->
                    assertThat(detail.orderItemId()).isEqualTo(item.getId()));
        });
    }

    @Test
    @DisplayName("존재하지 않는 운영자 주문 상세를 조회하면 찾을 수 없음 오류를 반환한다")
    void when_admin_order_does_not_exist_not_found_is_returned() {
        OrderNumber orderNumber = OrderNumber.from("ORD-0198f2a0-76c0-7000-8000-000000000120");
        when(orderQueryRepository.findDetailByOrderNumber(orderNumber)).thenReturn(Optional.empty());

        assertError(
                () -> orderQueryService.getAdminOrder(orderNumber),
                OrderErrorCode.ORDER_NOT_FOUND);
    }

    private static Order order(
            long sequence,
            UUID customerId,
            OrderStatus status,
            OrderItem... items) {
        Order order = Order.create(
                uuid(sequence),
                customerId,
                ShippingAddress.of(
                        "홍길동",
                        "010-1234-5678",
                        "06236",
                        "서울특별시 강남구 테헤란로 1",
                        "101동 1001호"),
                List.of(items),
                CREATED_AT);
        ReflectionTestUtils.setField(order, "status", status);
        ReflectionTestUtils.setField(order, "createdAt", CREATED_AT);
        return order;
    }

    private static OrderItem item(long sequence, UUID creatorId) {
        OrderItem item = OrderItem.create(
                uuid(1_000 + sequence),
                creatorId,
                uuid(2_000 + sequence),
                uuid(3_000 + sequence),
                uuid(4_000 + sequence),
                ProductSnapshot.of(
                        "아크릴 스탠드 " + sequence,
                        "A 타입",
                        Money.won(10_000),
                        2),
                Money.won(1_000));
        ReflectionTestUtils.setField(item, "createdAt", CREATED_AT.plusSeconds(sequence));
        return item;
    }

    private static void assertError(ThrowingCallable action, OrderErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isSameAs(expected));
    }

    private static UUID uuid(long sequence) {
        return UUID.fromString("0198f2a0-76c0-7000-8000-%012x".formatted(sequence));
    }
}
