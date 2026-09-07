package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.sub9.orderservice.cart.application.service.CartService;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderQueryRepository;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Instant;
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
        CartService.class,
        CouponApplicationPort.class,
        CouponUsagePort.class,
        StockPort.class,
        PaymentCancellationPort.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("주문 결제 결과 반영 PostgreSQL 연동")
class OrderPaymentResultServiceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-04T00:00:00Z");
    private static final UUID USER_COUPON_ID = uuid(900);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_payment_result_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private OrderPaymentResultService paymentResultService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderQueryRepository orderQueryRepository;

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
    @DisplayName("결제 성공 상태와 처리 시각을 같은 트랜잭션으로 저장한다")
    void when_payment_succeeds_status_and_paid_at_are_persisted() {
        Order order = order(10, null);
        orderRepository.save(order);
        Instant processedAt = order.getExpiresAt().minusSeconds(1);

        paymentResultService.markPaid(order.getId(), processedAt);

        Order saved = orderQueryRepository.findDetailByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getPaidAt()).isEqualTo(processedAt);
    }

    @Test
    @DisplayName("결제 실패 상태와 쿠폰 복구를 함께 처리하고 재고 복구 명령을 반환한다")
    void when_payment_fails_status_and_coupon_restore_are_applied() {
        Order order = order(20, USER_COUPON_ID);
        orderRepository.save(order);

        var command = paymentResultService.markPaymentFailed(
                order.getId(), order.getExpiresAt().minusSeconds(1));

        assertThat(status(order.getId())).isEqualTo(OrderStatus.FAILED.name());
        assertThat(command.orderId()).isEqualTo(order.getId());
        assertThat(command.items()).hasSize(1);
        verify(couponUsagePort).restore(order.getId(), List.of(USER_COUPON_ID));
    }

    @Test
    @DisplayName("쿠폰 복구가 실패하면 주문의 실패 상태도 저장하지 않는다")
    void when_coupon_restore_fails_order_status_is_rolled_back() {
        Order order = order(30, USER_COUPON_ID);
        orderRepository.save(order);
        doThrow(new IllegalStateException("쿠폰 복구 실패"))
                .when(couponUsagePort)
                .restore(order.getId(), List.of(USER_COUPON_ID));

        assertThatThrownBy(() -> paymentResultService.markPaymentFailed(
                order.getId(), order.getExpiresAt().minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("쿠폰 복구 실패");

        assertThat(status(order.getId())).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
        Order saved = orderQueryRepository.findDetailByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(saved.getPaidAt()).isNull();
    }

    private String status(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "select status from p_orders where id = ?", String.class, orderId);
    }

    private static Order order(long sequence, UUID userCouponId) {
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
                CREATED_AT);
    }

    private static UUID uuid(long sequence) {
        return UUID.fromString("0198f2a0-76c0-7000-8000-%012x".formatted(sequence));
    }
}
