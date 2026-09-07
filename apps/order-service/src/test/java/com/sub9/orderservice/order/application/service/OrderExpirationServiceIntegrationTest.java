package com.sub9.orderservice.order.application.service;

import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.domain.model.*;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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
    CartQueryService.class,
        CouponApplicationPort.class,
        CouponUsagePort.class,
        StockPort.class,
        PaymentCancellationPort.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("주문 만료 PostgreSQL 연동")
class OrderExpirationServiceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-04T00:00:00Z");
    private static final UUID USER_COUPON_ID = uuid(900);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_expiration_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private OrderExpirationTransactionService expirationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CouponUsagePort couponUsagePort;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void cleanDatabase() {
        reset(couponUsagePort);
        jdbcTemplate.update("delete from p_order_items");
        jdbcTemplate.update("delete from p_orders");
    }

    @Test
    @DisplayName("만료 후보를 오래된 순서로 제한해 조회한다")
    void when_expired_candidates_are_queried_only_oldest_pending_orders_are_returned() {
        Order oldest = order(10, CREATED_AT.minusSeconds(120), null);
        Order next = order(20, CREATED_AT.minusSeconds(60), null);
        Order last = order(30, CREATED_AT, null);
        Order future = order(40, CREATED_AT.plusSeconds(601), null);
        Order paid = order(50, CREATED_AT.minusSeconds(180), null);
        paid.markPaid(paid.getExpiresAt().minusSeconds(1));
        List.of(oldest, next, last, future, paid).forEach(orderRepository::save);

        List<UUID> result = orderRepository.findExpiredPendingOrderIds(
                CREATED_AT.plusSeconds(601), 2);

        assertThat(result).containsExactly(oldest.getId(), next.getId());
    }

    @Test
    @DisplayName("만료 상태와 쿠폰 복구를 같은 트랜잭션으로 저장한다")
    void when_order_expires_status_and_coupon_restore_are_applied() {
        Order order = order(60, CREATED_AT, USER_COUPON_ID);
        orderRepository.save(order);

        var result = expirationService.expire(order.getId(), order.getExpiresAt());

        assertThat(result).isPresent();
        assertThat(status(order.getId())).isEqualTo(OrderStatus.EXPIRED.name());
        verify(couponUsagePort).restore(order.getId(), List.of(USER_COUPON_ID));
    }

    @Test
    @DisplayName("쿠폰 복구가 실패하면 주문 만료 상태도 저장하지 않는다")
    void when_coupon_restore_fails_expiration_is_rolled_back() {
        Order order = order(70, CREATED_AT, USER_COUPON_ID);
        orderRepository.save(order);
        doThrow(new IllegalStateException("쿠폰 복구 실패"))
                .when(couponUsagePort)
                .restore(order.getId(), List.of(USER_COUPON_ID));

        assertThatThrownBy(() -> expirationService.expire(order.getId(), order.getExpiresAt()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("쿠폰 복구 실패");

        assertThat(status(order.getId())).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
    }

    private String status(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "select status from p_orders where id = ?", String.class, orderId);
    }

    private static Order order(long sequence, Instant createdAt, UUID userCouponId) {
        return Order.create(
                uuid(sequence),
                uuid(sequence + 1_000),
                ShippingAddress.of(
                        "홍길동", "010-1234-5678", "06236", "서울특별시 강남구", "101호"),
                List.of(OrderItem.create(
                        uuid(sequence + 1),
                        uuid(sequence + 2_000),
                        uuid(sequence + 2_500),
                        uuid(sequence + 3_000),
                        userCouponId,
                        ProductSnapshot.of("아크릴 스탠드", "A 타입", Money.won(18_000), 2),
                        Money.won(1_800))),
                createdAt);
    }

    private static UUID uuid(long sequence) {
        return UUID.fromString("0198f2a0-76c0-7000-8000-%012x".formatted(sequence));
    }
}
