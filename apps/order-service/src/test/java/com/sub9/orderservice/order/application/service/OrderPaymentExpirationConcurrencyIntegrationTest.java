package com.sub9.orderservice.order.application.service;

import static com.sub9.orderservice.support.PostgresConcurrencySupport.await;
import static com.sub9.orderservice.support.PostgresConcurrencySupport.awaitOrderLock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.ArgumentMatchers.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.order.infrastructure.persistence.OrderRepositoryAdapter;
import com.sub9.orderservice.order.infrastructure.scheduling.OrderExpirationScheduler;
import com.sub9.orderservice.payment.application.service.MockPaymentService;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
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
        CartQueryService.class,
        CouponApplicationPort.class,
        StockPort.class,
        KafkaTemplate.class,
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
    private MockPaymentService paymentService;

    @Autowired
    private OrderExpirationTransactionService expirationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired private EntityManager em;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private OrderRepositoryAdapter lockedOrders;

    @Autowired
    private StockPort stockPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @BeforeEach
    void setKafkaResult() {
        org.mockito.Mockito.clearInvocations(kafka, stockPort);
        when(clock.instant()).thenReturn(CREATED_AT.plusSeconds(30));
        when(kafka.send(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from p_payment_cancellations");
        jdbcTemplate.update("delete from p_payments");
        jdbcTemplate.update("delete from p_order_cart_cleanup_tasks");
        jdbcTemplate.update("delete from p_order_items");
        jdbcTemplate.update("delete from p_orders");
        jdbcTemplate.update("delete from p_user_coupons");
        jdbcTemplate.update("delete from p_coupons");
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("결제가 선점하면 결제와 쿠폰을 확정하고 만료 처리는 건너뛴다")
    void when_payment_locks_first_payment_and_coupon_are_committed(PaymentStatus result) throws Exception {
        Order order = savedOrder(10);
        var race = runRace(order.getId(),
                () -> paymentService.process(order.getCustomerId(), order.getOrderNumber(), result),
                () -> expire(order));

        assertThat(race.winner().status()).isEqualTo(result);
        assertThat(race.contender().failure()).isNull();
        assertFinalState(order, result == PaymentStatus.SUCCESS ? "PAID" : "FAILED", result);
        if (result == PaymentStatus.FAILED) {
            verify(stockPort).restore(eq(order.getId()), anyList(), eq(RestoreReason.PAYMENT_FAILED));
        } else {
            verify(kafka).send(eq("order.paid"), eq(order.getId().toString()), anyString());
        }
        verify(kafka).send(eq("order.notification"), eq(order.getId().toString()), anyString());
        verifyNoMoreInteractions(stockPort, kafka);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("만료가 선점하면 결제 행 없이 쿠폰을 복구하고 뒤늦은 결제를 거부한다")
    void when_expiration_locks_first_no_payment_is_saved(PaymentStatus result) throws Exception {
        Order order = savedOrder(20);
        var race = runRace(order.getId(), () -> expire(order),
                () -> paymentService.process(order.getCustomerId(), order.getOrderNumber(), result));

        assertExpiredError(race.contender().failure());
        assertFinalState(order, "EXPIRED", null);
        verify(stockPort).restore(eq(order.getId()), anyList(), eq(RestoreReason.ORDER_EXPIRED));
        verify(kafka).send(eq("order.notification"), eq(order.getId().toString()), anyString());
        verifyNoMoreInteractions(stockPort, kafka);
    }

    private Void expire(Order order) {
        new OrderExpirationScheduler(orderRepository, expirationService, stockPort,
                Clock.fixed(order.getExpiresAt(), ZoneOffset.UTC)).expireOrders();
        return null;
    }

    private void assertFinalState(Order order, String expected, PaymentStatus payment) {
        assertThat(status(order.getId())).isEqualTo(expected);
        assertThat(jdbcTemplate.queryForList("select status from p_payments", String.class))
                .containsExactlyElementsOf(payment == null ? List.of() : List.of(payment.name()));
        assertThat(jdbcTemplate.queryForObject("select count(*) from p_payment_cancellations", Integer.class)).isZero();
        var coupon = jdbcTemplate.queryForMap("select status, used_at, order_id from p_user_coupons where id = ?",
                USER_COUPON_ID);
        boolean paid = expected.equals("PAID");
        assertThat(coupon.get("status")).isEqualTo(paid ? "USED" : "ISSUED");
        assertThat(coupon.get("order_id")).isEqualTo(paid ? order.getId() : null);
        if (paid) {
            assertThat(coupon.get("used_at")).isNotNull();
            assertThat(paidAt(order.getId())).isNotNull();
        } else {
            assertThat(coupon.get("used_at")).isNull();
            assertThat(paidAt(order.getId())).isNull();
        }
    }

    private <W, C> RaceResult<W, C> runRace(
            UUID orderId,
            Supplier<W> winner,
            Supplier<C> contender) throws Exception {
        CountDownLatch winnerLocked = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        org.mockito.stubbing.Answer<Object> pause = call -> {
            Object value = call.callRealMethod();
            if (first.getAndSet(false)) {
                winnerLocked.countDown();
                await(releaseWinner);
            }
            return value;
        };
        doAnswer(pause).when(lockedOrders).findByIdForUpdate(orderId);
        doAnswer(pause).when(lockedOrders).findByOrderNumberForUpdate(any());
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<W> winnerFuture = executor.submit(winner::get);
                assertThat(winnerLocked.await(5, SECONDS)).isTrue();
                Future<Attempt<C>> contenderFuture = executor.submit(() -> {
                    try {
                        return new Attempt<>(contender.get(), null);
                    } catch (RuntimeException exception) {
                        return new Attempt<>(null, exception);
                    }
                });
                awaitOrderLock(jdbcTemplate, contenderFuture);
                releaseWinner.countDown();
                return new RaceResult<>(winnerFuture.get(10, SECONDS), contenderFuture.get(10, SECONDS));
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
                        uuid(sequence + 1), null,
                        uuid(sequence + 2_000),
                        uuid(sequence + 2_500),
                        uuid(sequence + 3_000),
                        USER_COUPON_ID,
                        ProductSnapshot.of("아크릴 스탠드", "A 타입", Money.won(18_000), 2),
                        Money.won(1_800))),
                CREATED_AT);
        return transaction().execute(ignored -> {
            Coupon coupon = Coupon.create(uuid(sequence + 5000), "만료 경쟁 쿠폰", 5, 10,
                    CREATED_AT.minusSeconds(60), CREATED_AT.plusSeconds(3600), order.getCustomerId(), CREATED_AT);
            em.persist(coupon);
            UserCoupon issued = UserCoupon.issue(USER_COUPON_ID, coupon, order.getCustomerId(), CREATED_AT);
            issued.use(order.getCustomerId(), order.getId(), CREATED_AT);
            em.persist(issued);
            return orderRepository.save(order);
        });
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

    private static UUID uuid(long sequence) {
        return UUID.fromString("0198f2a0-76c0-7000-8000-%012x".formatted(sequence));
    }

    private record Attempt<T>(T value, RuntimeException failure) {
    }

    private record RaceResult<W, C>(W winner, Attempt<C> contender) {
    }
}
