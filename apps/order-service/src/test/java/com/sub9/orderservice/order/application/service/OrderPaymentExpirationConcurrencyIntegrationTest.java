package com.sub9.orderservice.order.application.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
        StockPort.class,
        PaymentCancellationPort.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("결제 결과와 주문 만료 PostgreSQL 경쟁")
class OrderPaymentExpirationConcurrencyIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-04T00:00:00Z");
    private static final UUID USER_COUPON_ID = uuid(900);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_payment_expiration_concurrency_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private OrderPaymentResultService paymentResultService;

    @Autowired
    private OrderExpirationTransactionService expirationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CouponUsagePort couponUsagePort;

    @Autowired
    private StockPort stockPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void cleanDatabase() {
        reset(couponUsagePort, stockPort);
        jdbcTemplate.update("delete from p_order_items");
        jdbcTemplate.update("delete from p_orders");
    }

    @Test
    @DisplayName("결제 성공이 잠금을 먼저 얻으면 만료 처리를 건너뛴다")
    void when_payment_success_locks_first_expiration_is_skipped() throws Exception {
        Order order = savedOrder(10);

        RaceResult<Void, Optional<StockRestoreCommand>> race = runRace(
                order.getId(),
                () -> {
                    paymentResultService.markPaid(
                            order.getId(), order.getExpiresAt().minusNanos(1));
                    return null;
                },
                () -> expirationService.expire(order.getId(), order.getExpiresAt()));

        assertThat(race.contender().failure()).isNull();
        assertThat(race.contender().value()).isEmpty();
        assertThat(status(order.getId())).isEqualTo(OrderStatus.PAID.name());
        assertThat(paidAt(order.getId())).isNotNull();
        verifyNoInteractions(couponUsagePort, stockPort);
    }

    @Test
    @DisplayName("주문 만료가 잠금을 먼저 얻으면 뒤늦은 결제 성공을 거부한다")
    void when_expiration_locks_first_payment_success_is_rejected() throws Exception {
        Order order = savedOrder(20);

        RaceResult<Optional<StockRestoreCommand>, Void> race = runRace(
                order.getId(),
                () -> expirationService.expire(order.getId(), order.getExpiresAt()),
                () -> {
                    paymentResultService.markPaid(
                            order.getId(), order.getExpiresAt().minusNanos(1));
                    return null;
                });

        StockRestoreCommand command = race.winner().orElseThrow();
        assertExpiredError(race.contender().failure());
        assertThat(command.reason()).isEqualTo(RestoreReason.ORDER_EXPIRED);
        assertThat(status(order.getId())).isEqualTo(OrderStatus.EXPIRED.name());
        assertThat(paidAt(order.getId())).isNull();
        verify(couponUsagePort).restore(order.getId(), List.of(USER_COUPON_ID));
        verifyNoInteractions(stockPort);
    }

    @Test
    @DisplayName("결제 실패가 잠금을 먼저 얻으면 실패 복구만 남기고 만료를 건너뛴다")
    void when_payment_failure_locks_first_expiration_is_skipped() throws Exception {
        Order order = savedOrder(30);

        RaceResult<StockRestoreCommand, Optional<StockRestoreCommand>> race = runRace(
                order.getId(),
                () -> paymentResultService.markPaymentFailed(
                        order.getId(), order.getExpiresAt().minusNanos(1)),
                () -> expirationService.expire(order.getId(), order.getExpiresAt()));

        assertThat(race.contender().failure()).isNull();
        assertThat(race.contender().value()).isEmpty();
        assertThat(race.winner().reason()).isEqualTo(RestoreReason.PAYMENT_FAILED);
        assertThat(status(order.getId())).isEqualTo(OrderStatus.FAILED.name());
        verify(couponUsagePort).restore(order.getId(), List.of(USER_COUPON_ID));
        verifyNoInteractions(stockPort);
    }

    @Test
    @DisplayName("주문 만료가 잠금을 먼저 얻으면 뒤늦은 결제 실패를 거부한다")
    void when_expiration_locks_first_payment_failure_is_rejected() throws Exception {
        Order order = savedOrder(40);

        RaceResult<Optional<StockRestoreCommand>, StockRestoreCommand> race = runRace(
                order.getId(),
                () -> expirationService.expire(order.getId(), order.getExpiresAt()),
                () -> paymentResultService.markPaymentFailed(
                        order.getId(), order.getExpiresAt().minusNanos(1)));

        StockRestoreCommand command = race.winner().orElseThrow();
        assertExpiredError(race.contender().failure());
        assertThat(command.reason()).isEqualTo(RestoreReason.ORDER_EXPIRED);
        assertThat(status(order.getId())).isEqualTo(OrderStatus.EXPIRED.name());
        verify(couponUsagePort).restore(order.getId(), List.of(USER_COUPON_ID));
        verifyNoInteractions(stockPort);
    }

    private <W, C> RaceResult<W, C> runRace(
            UUID orderId,
            Supplier<W> winner,
            Supplier<C> contender) throws Exception {
        CountDownLatch winnerLocked = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        CountDownLatch contenderStarted = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<W> winnerFuture = executor.submit(() -> transaction().execute(status -> {
                    orderRepository.findByIdForUpdate(orderId).orElseThrow();
                    winnerLocked.countDown();
                    await(releaseWinner);
                    return winner.get();
                }));

                assertThat(winnerLocked.await(5, SECONDS)).isTrue();
                Future<Attempt<C>> contenderFuture = executor.submit(() -> {
                    contenderStarted.countDown();
                    try {
                        return new Attempt<>(contender.get(), null);
                    } catch (RuntimeException exception) {
                        return new Attempt<>(null, exception);
                    }
                });

                assertThat(contenderStarted.await(5, SECONDS)).isTrue();
                assertThatThrownBy(() -> contenderFuture.get(200, MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                releaseWinner.countDown();

                return new RaceResult<>(
                        winnerFuture.get(5, SECONDS),
                        contenderFuture.get(5, SECONDS));
            } finally {
                releaseWinner.countDown();
            }
        }
    }

    private Order savedOrder(long sequence) {
        Order order = Order.create(
                uuid(sequence),
                uuid(sequence + 1_000),
                ShippingAddress.of(
                        "홍길동", "010-1234-5678", "06236", "서울특별시 강남구", "101호"),
                List.of(OrderItem.create(
                        uuid(sequence + 1),
                        uuid(sequence + 2_000),
                        uuid(sequence + 2_500),
                        uuid(sequence + 3_000),
                        USER_COUPON_ID,
                        ProductSnapshot.of("아크릴 스탠드", "A 타입", Money.won(18_000), 2),
                        Money.won(1_800))),
                CREATED_AT);
        return orderRepository.save(order);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private String status(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "select status from p_orders where id = ?", String.class, orderId);
    }

    private Object paidAt(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "select paid_at from p_orders where id = ?", Object.class, orderId);
    }

    private static void assertExpiredError(RuntimeException exception) {
        assertThat(exception)
                .isInstanceOfSatisfying(BusinessException.class,
                        businessException -> assertThat(businessException.getErrorCode())
                                .isSameAs(OrderErrorCode.ORDER_ALREADY_EXPIRED));
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

    private static UUID uuid(long sequence) {
        return UUID.fromString("0198f2a0-76c0-7000-8000-%012x".formatted(sequence));
    }

    private record Attempt<T>(T value, RuntimeException failure) {
    }

    private record RaceResult<W, C>(W winner, Attempt<C> contender) {
    }
}
