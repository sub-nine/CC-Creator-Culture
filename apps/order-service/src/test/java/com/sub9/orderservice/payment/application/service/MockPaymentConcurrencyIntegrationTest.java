package com.sub9.orderservice.payment.application.service;

import static com.sub9.orderservice.support.PostgresConcurrencySupport.await;
import static com.sub9.orderservice.support.PostgresConcurrencySupport.awaitOrderLock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.order.infrastructure.persistence.OrderRepositoryAdapter;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import com.sub9.orderservice.payment.application.dto.MockPaymentResult;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
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
        CartQueryService.class,
        CouponApplicationPort.class,
        PaymentCancellationPort.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("동일 주문 동시 결제 PostgreSQL 경쟁")
class MockPaymentConcurrencyIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-07T00:00:00Z");
    private static final Instant NOW = CREATED_AT.plusSeconds(30);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("mock_payment_concurrency_test")
            .withUsername("test")
            .withPassword("test");

    private final UuidV7Generator ids = new UuidV7Generator();

    @Autowired
    private MockPaymentService service;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private OrderRepositoryAdapter lockedOrders;

    @MockitoBean
    private StockPort stock;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private KafkaTemplate<String, String> kafka;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setClock() {
        reset(stock, kafka);
        when(clock.instant()).thenReturn(NOW);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void cleanDatabase() {
        jdbc.update("delete from public.p_payment_cancellations");
        jdbc.update("delete from public.p_payments");
        jdbc.update("delete from p_order_cart_cleanup_tasks");
        jdbc.update("delete from p_order_items");
        jdbc.update("delete from p_orders");
        jdbc.update("delete from p_user_coupons");
        jdbc.update("delete from p_coupons");
    }

    @Test
    @DisplayName("동시에 들어온 성공 결제는 한 건만 저장하고 같은 결과를 반환한다")
    void when_success_payments_overlap_one_payment_is_reused() throws Exception {
        Order order = saveOrder();

        List<Attempt<MockPaymentResult>> attempts = runConcurrent(
                order, PaymentStatus.SUCCESS, PaymentStatus.SUCCESS);

        assertThat(attempts).allSatisfy(attempt -> {
            assertThat(attempt.failure()).isNull();
            assertThat(attempt.value().status()).isEqualTo(PaymentStatus.SUCCESS);
        });
        assertThat(attempts.get(0).value()).isEqualTo(attempts.get(1).value());
        assertThat(paymentCount()).isEqualTo(1);
        assertThat(orderStatus(order)).isEqualTo("PAID");
        verify(kafka).send(eq("order.paid"), eq(order.getId().toString()), anyString());
        verifyNoInteractions(stock);
        assertCoupon(order, true);
        verify(kafka).send(eq("order.notification"), eq(order.getId().toString()), anyString());
        verifyNoMoreInteractions(kafka);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("성공과 실패 결제가 경쟁하면 먼저 확정된 결과만 남긴다")
    void when_success_and_failure_overlap_only_the_first_result_is_kept(PaymentStatus winner) throws Exception {
        Order order = saveOrder();

        List<Attempt<MockPaymentResult>> attempts = runConcurrent(
                order, winner, winner == PaymentStatus.SUCCESS ? PaymentStatus.FAILED : PaymentStatus.SUCCESS);

        Attempt<MockPaymentResult> committed = attempts.stream()
                .filter(attempt -> attempt.value() != null)
                .findFirst()
                .orElseThrow();
        Attempt<MockPaymentResult> rejected = attempts.stream()
                .filter(attempt -> attempt.failure() != null)
                .findFirst()
                .orElseThrow();
        String status = orderStatus(order);

        assertThat(status).isEqualTo(winner == PaymentStatus.SUCCESS ? "PAID" : "FAILED");
        assertThat(committed.value().status().name())
                .isEqualTo(status.equals("PAID") ? "SUCCESS" : "FAILED");
        assertThat(rejected.failure()).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS));
        assertThat(paymentCount()).isEqualTo(1);
        if (status.equals("PAID")) {
            verifyNoInteractions(stock);
            verify(kafka).send(eq("order.paid"), eq(order.getId().toString()), anyString());
        } else {
            verify(stock).restore(eq(order.getId()), eq(stockItems(order)), eq(RestoreReason.PAYMENT_FAILED));
            verify(kafka, never()).send(eq("order.paid"), anyString(), anyString());
        }
        assertCoupon(order, winner == PaymentStatus.SUCCESS);
        assertThat(jdbc.queryForObject("select status from p_payments", String.class)).isEqualTo(winner.name());
        verify(kafka).send(eq("order.notification"), eq(order.getId().toString()), anyString());
        verifyNoMoreInteractions(stock, kafka);
    }

    private List<Attempt<MockPaymentResult>> runConcurrent(
            Order order, PaymentStatus firstStatus, PaymentStatus secondStatus) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(call -> {
            Object orderResult = call.callRealMethod();
            if (first.getAndSet(false)) {
                locked.countDown();
                await(release);
            }
            return orderResult;
        }).when(lockedOrders).findByOrderNumberForUpdate(order.getOrderNumber());
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<Attempt<MockPaymentResult>> winner = executor.submit(() -> attempt(order, firstStatus));
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                Future<Attempt<MockPaymentResult>> contender = executor.submit(() -> attempt(order, secondStatus));
                awaitOrderLock(jdbc, contender);
                release.countDown();
                return List.of(winner.get(10, TimeUnit.SECONDS), contender.get(10, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    private Attempt<MockPaymentResult> attempt(Order order, PaymentStatus status) {
        try {
            return new Attempt<>(service.process(order.getCustomerId(), order.getOrderNumber(), status), null);
        } catch (RuntimeException exception) {
            return new Attempt<>(null, exception);
        }
    }

    private void assertCoupon(Order order, boolean used) {
        var coupon = jdbc.queryForMap("select status, order_id, used_at from p_user_coupons where id = ?",
                order.getItems().getFirst().getUserCouponId());
        assertThat(coupon.get("status")).isEqualTo(used ? "USED" : "ISSUED");
        assertThat(coupon.get("order_id")).isEqualTo(used ? order.getId() : null);
        if (used) assertThat(coupon.get("used_at")).isNotNull();
        else assertThat(coupon.get("used_at")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from p_payment_cancellations", Integer.class)).isZero();
    }

    private int paymentCount() {
        return jdbc.queryForObject("select count(*) from public.p_payments", Integer.class);
    }

    private String orderStatus(Order order) {
        return jdbc.queryForObject("select status from p_orders where id = ?", String.class, order.getId());
    }

    private List<StockItem> stockItems(Order order) {
        return List.of(new StockItem(order.getItems().getFirst().getSkuId(), 2));
    }

    private Order saveOrder() {
        return new TransactionTemplate(transactionManager).execute(ignored -> {
            UUID orderId = ids.generate();
            UUID customer = ids.generate();
            Coupon coupon = Coupon.create(ids.generate(), "경쟁 쿠폰", 10, 10,
                    CREATED_AT.minusSeconds(60), CREATED_AT.plusSeconds(3600), customer, CREATED_AT);
            em.persist(coupon);
            UserCoupon issued = UserCoupon.issue(ids.generate(), coupon, customer, CREATED_AT);
            issued.use(customer, orderId, CREATED_AT);
            em.persist(issued);
            Order order = Order.create(orderId, customer,
                    ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울 강남구", "101호"),
                    List.of(OrderItem.create(ids.generate(), null, ids.generate(), ids.generate(), ids.generate(),
                            issued.getId(), ProductSnapshot.of("키링", "기본", Money.won(20_000), 2), Money.won(4_000))),
                    CREATED_AT);
            return orders.save(order);
        });
    }

    private record Attempt<T>(T value, RuntimeException failure) {
    }
}
