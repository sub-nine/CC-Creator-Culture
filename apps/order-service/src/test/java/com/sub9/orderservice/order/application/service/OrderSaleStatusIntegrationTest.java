package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import com.sub9.orderservice.cart.application.dto.CartProductInfo;
import com.sub9.orderservice.cart.application.port.out.CartProductPort;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.infrastructure.persistence.CartJpaRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sub9.orderservice.cart.application.service.CartCommandService;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.AppliedCoupon;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'",
        "management.tracing.export.enabled=false"
})
@MockitoBean(types = {
        CartProductPort.class,
        CouponApplicationPort.class,
        CouponUsagePort.class,
        StockPort.class,
        PaymentCancellationPort.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("판매 상태에 따른 주문 생성 PostgreSQL 연동")
class OrderSaleStatusIntegrationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_creation_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private OrderCreationService orderCreationService;

    @Autowired
    private CartProductPort cartProductPort;

    @Autowired private CartCommandService cartCommandService;
    @Autowired private CartQueryService cartQueryService;
    @Autowired private CartJpaRepository cartRepository;

    @Autowired
    private CouponApplicationPort couponApplicationPort;

    @Autowired
    private CouponUsagePort couponUsagePort;

    @Autowired
    private StockPort stockPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from p_order_command_requests");
        jdbcTemplate.update("delete from p_order_items");
        jdbcTemplate.update("delete from p_orders");
        cartRepository.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "SUSPENDED"})
    @DisplayName("장바구니 추가 후 일부 상품이 판매 중지되면 전체 주문을 거부하고 실패를 재사용한다")
    void when_sale_stops_after_cart_add_order_is_rejected_and_failure_is_replayed(String status) {
        Cart first = addCart();
        Cart second = addCart();
        UUID couponId = UUID.randomUUID();
        CreateOrderCommand command = command(first, second, couponId);
        List<CartProductInfo> active = List.of(product(first, "ACTIVE"), product(second, "ACTIVE"));
        when(cartProductPort.getCartItemProducts(anyList())).thenReturn(active);
        assertThat(cartQueryService.getCart(CUSTOMER_ID)).hasSize(2);

        when(cartProductPort.getCartItemProducts(anyList()))
                .thenReturn(List.of(active.getFirst(), product(second, status)));
        assertThat(cartQueryService.getCart(CUSTOMER_ID))
                .extracting(com.sub9.orderservice.cart.presentation.response.CartItemResponse::productStatus)
                .containsExactlyInAnyOrder("ACTIVE", status);
        assertThatThrownBy(() -> orderCreationService.create(CUSTOMER_ID, "stopped", command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.PRODUCT_NOT_FOR_SALE));

        assertThat(count("p_orders")).isZero();
        assertThat(count("p_order_items")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select status from p_order_command_requests", String.class)).isEqualTo("FAILED");
        verifyNoInteractions(couponApplicationPort, couponUsagePort, stockPort);

        // 판매가 재개돼도 기존 키는 저장된 실패를 재사용한다.
        when(cartProductPort.getCartItemProducts(anyList())).thenReturn(active);
        OrderCreationResult replay = orderCreationService.create(CUSTOMER_ID, "stopped", command);
        assertThat(replay.httpStatus()).isEqualTo(400);
        assertThat(replay.responseBody()).asString().contains("ORDER_0011");
        verifyNoInteractions(couponApplicationPort, couponUsagePort, stockPort);
        verify(cartProductPort, times(3)).getCartItemProducts(anyList());

        when(couponApplicationPort.apply(eq(CUSTOMER_ID), anyList()))
                .thenReturn(List.of(new AppliedCoupon(first.getId(), couponId, 100)));
        OrderCreationResult created = orderCreationService.create(CUSTOMER_ID, "resumed", command);
        assertThat(created.httpStatus()).isEqualTo(201);
        assertThat(count("p_orders")).isEqualTo(1);
        assertThat(count("p_order_items")).isEqualTo(2);
        verify(cartProductPort, times(4)).getCartItemProducts(anyList());
        verify(stockPort).deduct(any(), anyList());
        verify(couponUsagePort).markUsed(any(), anyList());
    }

    @Test
    @DisplayName("선택하지 않은 상품이 판매 중지여도 선택한 판매 중 상품의 주문은 성공한다")
    void when_unselected_product_is_stopped_active_selection_is_created() {
        Cart selected = addCart();
        Cart unselected = addCart();
        when(cartProductPort.getCartItemProducts(anyList()))
                .thenReturn(List.of(product(selected, "ACTIVE"), product(unselected, "SUSPENDED")));
        assertThat(cartQueryService.getCart(CUSTOMER_ID)).hasSize(2);
        when(cartProductPort.getCartItemProducts(List.of(selected.getSkuId())))
                .thenReturn(List.of(product(selected, "ACTIVE")));
        CreateOrderCommand command = new CreateOrderCommand(
                List.of(new CreateOrderCommand.Item(selected.getId(), null)), address());

        assertThat(orderCreationService.create(CUSTOMER_ID, "active", command).httpStatus()).isEqualTo(201);
        assertThat(count("p_orders")).isEqualTo(1);
        assertThat(count("p_order_items")).isEqualTo(1);
        verify(stockPort).deduct(any(), anyList());
    }

    private Cart addCart() {
        UUID skuId = UUID.randomUUID();
        cartCommandService.addCartItem(new AddCartItemCommand(CUSTOMER_ID, skuId, 1));
        return cartRepository.findAll().stream().filter(cart -> cart.getSkuId().equals(skuId))
                .findFirst().orElseThrow();
    }

    private CartProductInfo product(Cart cart, String status) {
        return new CartProductInfo(cart.getSkuId(), UUID.randomUUID(), UUID.randomUUID(),
                "상품", "옵션", status, 3000);
    }

    private CreateOrderCommand command(Cart first, Cart second, UUID couponId) {
        return new CreateOrderCommand(List.of(new CreateOrderCommand.Item(first.getId(), couponId),
                new CreateOrderCommand.Item(second.getId(), null)), address());
    }

    private CreateOrderCommand.ShippingAddress address() {
        return new CreateOrderCommand.ShippingAddress(
                "홍길동", "010-1234-5678", "06236", "서울특별시 강남구 테헤란로 1", "101호");
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }
}
