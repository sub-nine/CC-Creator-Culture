package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.orderservice.order.application.port.output.CartSnapshotPort;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderItemStatus;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
@DisplayName("창작자 주문 상품 상태 변경 PostgreSQL 연동")
class OrderItemStatusServiceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-04T00:00:00Z");
    private static final UUID FIRST_CREATOR_ID = uuid(1);
    private static final UUID SECOND_CREATOR_ID = uuid(2);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_item_status_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private OrderItemStatusService orderItemStatusService;

    @Autowired
    private OrderRepository orderRepository;

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
        jdbcTemplate.update("delete from p_order_items");
        jdbcTemplate.update("delete from p_orders");
    }

    @Test
    @DisplayName("여러 주문 상품을 순서대로 변경하고 상위 주문 상태를 함께 저장한다")
    void when_multiple_items_are_changed_parent_status_and_repeated_result_are_persisted() {
        Order order = order();
        orderRepository.save(order);
        markPaid(order.getId());
        OrderItem first = order.getItems().get(0);
        OrderItem second = order.getItems().get(1);

        var started = orderItemStatusService.update(
                FIRST_CREATOR_ID, first.getId(), OrderItemStatus.PREPARING);
        var replay = orderItemStatusService.update(
                FIRST_CREATOR_ID, first.getId(), OrderItemStatus.PREPARING);

        assertThat(started.orderStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(replay.status()).isEqualTo(OrderItemStatus.PREPARING);
        completeFromPreparing(FIRST_CREATOR_ID, first.getId());
        completeFromOrdered(SECOND_CREATOR_ID, second.getId());

        var completedReplay = orderItemStatusService.update(
                SECOND_CREATOR_ID, second.getId(), OrderItemStatus.COMPLETED);

        assertThat(completedReplay.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.COMPLETED.name());
        assertThat(itemStatuses(order.getId())).containsExactlyInAnyOrder(
                OrderItemStatus.COMPLETED.name(),
                OrderItemStatus.COMPLETED.name());
    }

    private void completeFromPreparing(UUID creatorId, UUID orderItemId) {
        orderItemStatusService.update(creatorId, orderItemId, OrderItemStatus.SHIPPED);
        orderItemStatusService.update(creatorId, orderItemId, OrderItemStatus.DELIVERED);
        orderItemStatusService.update(creatorId, orderItemId, OrderItemStatus.COMPLETED);
    }

    private void completeFromOrdered(UUID creatorId, UUID orderItemId) {
        orderItemStatusService.update(creatorId, orderItemId, OrderItemStatus.PREPARING);
        completeFromPreparing(creatorId, orderItemId);
    }

    private void markPaid(UUID orderId) {
        jdbcTemplate.update(
                "update p_orders set status = 'PAID', paid_at = ? where id = ?",
                LocalDateTime.ofInstant(CREATED_AT.plusSeconds(60), ZoneOffset.UTC),
                orderId);
    }

    private String orderStatus(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "select status from p_orders where id = ?", String.class, orderId);
    }

    private List<String> itemStatuses(UUID orderId) {
        return jdbcTemplate.queryForList(
                "select status from p_order_items where order_id = ?", String.class, orderId);
    }

    private static Order order() {
        List<OrderItem> items = List.of(
                item(10, FIRST_CREATOR_ID),
                item(11, SECOND_CREATOR_ID));
        return Order.create(
                uuid(20),
                uuid(21),
                ShippingAddress.of(
                        "홍길동", "010-1234-5678", "06236", "서울특별시 강남구", "101호"),
                items,
                CREATED_AT);
    }

    private static OrderItem item(long sequence, UUID creatorId) {
        return OrderItem.create(
                uuid(sequence),
                creatorId,
                uuid(sequence + 2_000),
                uuid(sequence + 3_000),
                null,
                ProductSnapshot.of("아크릴 스탠드", "A 타입", Money.won(18_000), 2),
                Money.won(1_800));
    }

    private static UUID uuid(long sequence) {
        return UUID.fromString("0198f2a0-76c0-7000-8000-%012x".formatted(sequence));
    }
}
