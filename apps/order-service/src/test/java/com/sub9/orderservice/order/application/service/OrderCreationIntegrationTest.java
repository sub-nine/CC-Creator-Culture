package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort.CartItemSnapshot;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.AppliedCoupon;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockOperationUncertainException;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.domain.model.OrderCommandStatus;
import com.sub9.orderservice.order.presentation.response.CreateOrderResponse;
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
        CartSnapshotPort.class,
        CouponApplicationPort.class,
        CouponUsagePort.class,
        StockPort.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("주문 생성 PostgreSQL 연동")
class OrderCreationIntegrationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_creation_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private OrderCreationService orderCreationService;

    @Autowired
    private CartSnapshotPort cartSnapshotPort;

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
        jdbcTemplate.execute("alter table p_orders drop constraint if exists ck_orders_test_reject");
        jdbcTemplate.update("delete from p_order_command_requests");
        jdbcTemplate.update("delete from p_order_items");
        jdbcTemplate.update("delete from p_orders");
    }

    @Test
    @DisplayName("여러 창작자의 주문과 성공 멱등 결과를 함께 저장하고 반복 요청에 재사용한다")
    void when_order_is_created_success_and_replay_change_resources_once() {
        Fixture fixture = fixture();
        stubCollaborators(fixture);

        OrderCreationResult created = orderCreationService.create(
                CUSTOMER_ID, "create-success", fixture.command());
        OrderCreationResult replay = orderCreationService.create(
                CUSTOMER_ID, "create-success", fixture.command());

        assertThat(created.httpStatus()).isEqualTo(201);
        assertThat(created.responseBody()).isInstanceOfSatisfying(ApiResponse.class, response -> {
            assertThat(response.getMessage()).isEqualTo("주문 생성 성공");
            assertThat(response.getData()).isInstanceOfSatisfying(CreateOrderResponse.class, data -> {
                assertThat(data.originalAmount()).isEqualTo(41_000);
                assertThat(data.discountAmount()).isEqualTo(1_800);
                assertThat(data.paymentAmount()).isEqualTo(39_200);
            });
        });
        assertThat(replay.httpStatus()).isEqualTo(201);
        assertThat(commandStatus()).isEqualTo(OrderCommandStatus.SUCCEEDED.name());
        assertThat(count("p_orders")).isEqualTo(1);
        assertThat(count("p_order_items")).isEqualTo(2);
        verify(cartSnapshotPort, times(1)).getCartItems(eq(CUSTOMER_ID), anyList());
        verify(couponApplicationPort, times(1)).apply(eq(CUSTOMER_ID), anyList());
        verify(stockPort, times(1)).deduct(org.mockito.ArgumentMatchers.any(), anyList());
        verify(couponUsagePort, times(1)).markUsed(org.mockito.ArgumentMatchers.any(), anyList());
    }

    @Test
    @DisplayName("재고 차감 뒤 로컬 저장이 실패하면 재고를 복구하고 실패 결과를 재사용한다")
    void when_local_save_fails_stock_is_restored_and_failure_is_replayed() {
        Fixture fixture = fixture();
        stubCollaborators(fixture);
        rejectPendingOrders();

        assertThatThrownBy(() -> orderCreationService.create(
                CUSTOMER_ID, "create-failure", fixture.command()))
                .isInstanceOf(RuntimeException.class);

        OrderCreationResult replay = orderCreationService.create(
                CUSTOMER_ID, "create-failure", fixture.command());
        assertThat(replay.httpStatus()).isEqualTo(500);
        assertThat(commandStatus()).isEqualTo(OrderCommandStatus.FAILED.name());
        assertThat(count("p_orders")).isZero();
        assertThat(count("p_order_items")).isZero();
        verify(stockPort, times(1)).restore(
                org.mockito.ArgumentMatchers.any(),
                anyList(),
                eq(StockPort.RestoreReason.ORDER_CREATION_FAILED));
        verify(stockPort, times(1)).deduct(org.mockito.ArgumentMatchers.any(), anyList());
        verify(couponUsagePort, times(1)).markUsed(org.mockito.ArgumentMatchers.any(), anyList());
    }

    @Test
    @DisplayName("재고 복구 결과가 불명확하면 명령을 처리 중으로 유지한다")
    void when_stock_restore_is_uncertain_command_remains_processing() {
        Fixture fixture = fixture();
        stubCollaborators(fixture);
        rejectPendingOrders();
        doThrow(new StockOperationUncertainException("재고 복구 결과 불명"))
                .when(stockPort).restore(
                        org.mockito.ArgumentMatchers.any(),
                        anyList(),
                        eq(StockPort.RestoreReason.ORDER_CREATION_FAILED));

        assertThatThrownBy(() -> orderCreationService.create(
                CUSTOMER_ID, "create-uncertain", fixture.command()))
                .isInstanceOf(StockOperationUncertainException.class);

        assertThat(commandStatus()).isEqualTo(OrderCommandStatus.PROCESSING.name());
        assertThat(count("p_orders")).isZero();
        assertThat(count("p_order_items")).isZero();
    }

    private void rejectPendingOrders() {
        jdbcTemplate.execute("""
                alter table p_orders
                add constraint ck_orders_test_reject check (status <> 'PENDING_PAYMENT')
                """);
    }

    private void stubCollaborators(Fixture fixture) {
        when(cartSnapshotPort.getCartItems(eq(CUSTOMER_ID), anyList()))
                .thenReturn(fixture.snapshots());
        when(couponApplicationPort.apply(eq(CUSTOMER_ID), anyList()))
                .thenReturn(List.of(new AppliedCoupon(
                        fixture.firstCartItemId(),
                        fixture.userCouponId(),
                        1_800)));
    }

    private Fixture fixture() {
        UUID firstCartItemId = UUID.randomUUID();
        UUID secondCartItemId = UUID.randomUUID();
        UUID userCouponId = UUID.randomUUID();
        CreateOrderCommand command = new CreateOrderCommand(
                List.of(
                        new CreateOrderCommand.Item(firstCartItemId, userCouponId),
                        new CreateOrderCommand.Item(secondCartItemId, null)),
                new CreateOrderCommand.ShippingAddress(
                        "홍길동",
                        "010-1234-5678",
                        "06236",
                        "서울특별시 강남구 테헤란로 1",
                        "101동 1001호"));
        List<CartItemSnapshot> snapshots = List.of(
                new CartItemSnapshot(
                        firstCartItemId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "아크릴 스탠드",
                        "A 타입",
                        18_000,
                        2),
                new CartItemSnapshot(
                        secondCartItemId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "포토 카드",
                        "기본",
                        5_000,
                        1));
        return new Fixture(firstCartItemId, userCouponId, command, snapshots);
    }

    private String commandStatus() {
        return jdbcTemplate.queryForObject(
                "select status from p_order_command_requests",
                String.class);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private record Fixture(
            UUID firstCartItemId,
            UUID userCouponId,
            CreateOrderCommand command,
            List<CartItemSnapshot> snapshots
    ) {
    }
}
