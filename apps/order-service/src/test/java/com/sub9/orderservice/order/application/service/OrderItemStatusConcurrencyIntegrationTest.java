package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.cart.application.service.CartCommandService;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderItemStatus;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorOrderItemDetail;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
        CartCommandService.class,
        CouponApplicationPort.class,
        CouponUsagePort.class,
        StockPort.class,
        PaymentCancellationPort.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("창작자 주문 상품 상태 변경 PostgreSQL 경쟁")
class OrderItemStatusConcurrencyIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-04T00:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_item_status_concurrency_test")
            .withUsername("test")
            .withPassword("test");

    private final UuidV7Generator ids = new UuidV7Generator();

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
    @DisplayName("서로 다른 상품을 동시에 준비 중으로 바꿔도 둘 다 반영되고 주문은 처리 중이 된다")
    void when_two_items_become_preparing_together_order_becomes_processing() throws Exception {
        Order order = savedPaidOrder();
        OrderItem first = order.getItems().get(0);
        OrderItem second = order.getItems().get(1);

        List<CreatorOrderItemDetail> results = runConcurrent(
                () -> orderItemStatusService.update(
                        first.getCreatorId(), first.getId(), OrderItemStatus.PREPARING),
                () -> orderItemStatusService.update(
                        second.getCreatorId(), second.getId(), OrderItemStatus.PREPARING));

        assertThat(results).extracting(CreatorOrderItemDetail::status)
                .containsOnly(OrderItemStatus.PREPARING);
        assertThat(results).extracting(CreatorOrderItemDetail::orderStatus)
                .containsOnly(OrderStatus.PROCESSING);
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.PROCESSING.name());
        assertThat(itemStatuses(order.getId())).containsExactlyInAnyOrder(
                OrderItemStatus.PREPARING.name(), OrderItemStatus.PREPARING.name());
        assertThat(version(order.getId())).isGreaterThan(order.getVersion());
    }

    @Test
    @DisplayName("같은 상품을 동시에 준비 중으로 바꿔도 한 상태만 남기고 충돌 없이 완료한다")
    void when_same_item_becomes_preparing_together_status_is_kept_once() throws Exception {
        Order order = savedPaidOrder();
        OrderItem first = order.getItems().getFirst();

        List<CreatorOrderItemDetail> results = runConcurrent(
                () -> orderItemStatusService.update(
                        first.getCreatorId(), first.getId(), OrderItemStatus.PREPARING),
                () -> orderItemStatusService.update(
                        first.getCreatorId(), first.getId(), OrderItemStatus.PREPARING));

        assertThat(results).extracting(CreatorOrderItemDetail::status)
                .containsOnly(OrderItemStatus.PREPARING);
        assertThat(results).extracting(CreatorOrderItemDetail::orderItemId)
                .containsOnly(first.getId());
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.PROCESSING.name());
        assertThat(itemStatuses(order.getId())).containsExactlyInAnyOrder(
                OrderItemStatus.PREPARING.name(), OrderItemStatus.ORDERED.name());
    }

    private List<CreatorOrderItemDetail> runConcurrent(
            Supplier<CreatorOrderItemDetail> first,
            Supplier<CreatorOrderItemDetail> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<CreatorOrderItemDetail> firstFuture = executor.submit(() -> run(first, ready, start));
                Future<CreatorOrderItemDetail> secondFuture = executor.submit(() -> run(second, ready, start));
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                return List.of(firstFuture.get(10, TimeUnit.SECONDS), secondFuture.get(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
        }
    }

    private CreatorOrderItemDetail run(
            Supplier<CreatorOrderItemDetail> action,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        await(start);
        return action.get();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트가 중단되었습니다.", exception);
        }
    }

    private Order savedPaidOrder() {
        UUID firstCreator = ids.generate();
        UUID secondCreator = ids.generate();
        Order order = Order.create(
                ids.generate(),
                ids.generate(),
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울특별시 강남구", "101호"),
                List.of(item(firstCreator), item(secondCreator)),
                CREATED_AT);
        Order saved = orderRepository.save(order);
        markPaid(saved.getId());
        return saved;
    }

    private OrderItem item(UUID creatorId) {
        return OrderItem.create(
                ids.generate(), null,
                creatorId,
                ids.generate(),
                ids.generate(),
                null,
                ProductSnapshot.of("아크릴 스탠드", "A 타입", Money.won(18_000), 2),
                Money.won(1_800));
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

    private long version(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "select version from p_orders where id = ?", Long.class, orderId);
    }
}
