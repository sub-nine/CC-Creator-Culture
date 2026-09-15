package com.sub9.orderservice.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
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

    @MockitoBean
    private CouponUsagePort coupons;

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
        reset(coupons, stock, kafka);
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
        verifyNoInteractions(coupons, stock);
    }

    @Test
    @DisplayName("성공과 실패 결제가 경쟁하면 먼저 확정된 결과만 남긴다")
    void when_success_and_failure_overlap_only_the_first_result_is_kept() throws Exception {
        Order order = saveOrder();

        List<Attempt<MockPaymentResult>> attempts = runConcurrent(
                order, PaymentStatus.SUCCESS, PaymentStatus.FAILED);

        Attempt<MockPaymentResult> committed = attempts.stream()
                .filter(attempt -> attempt.value() != null)
                .findFirst()
                .orElseThrow();
        Attempt<MockPaymentResult> rejected = attempts.stream()
                .filter(attempt -> attempt.failure() != null)
                .findFirst()
                .orElseThrow();
        String status = orderStatus(order);

        assertThat(status).isIn("PAID", "FAILED");
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
        verifyNoInteractions(coupons);
    }

    private List<Attempt<MockPaymentResult>> runConcurrent(
            Order order, PaymentStatus firstStatus, PaymentStatus secondStatus) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<Attempt<MockPaymentResult>> first = executor.submit(
                        () -> attempt(order, firstStatus, ready, start));
                Future<Attempt<MockPaymentResult>> second = executor.submit(
                        () -> attempt(order, secondStatus, ready, start));
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
        }
    }

    private Attempt<MockPaymentResult> attempt(
            Order order, PaymentStatus status, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            return new Attempt<>(service.process(order.getCustomerId(), order.getOrderNumber(), status), null);
        } catch (RuntimeException exception) {
            return new Attempt<>(null, exception);
        }
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
        Order order = Order.create(ids.generate(), ids.generate(),
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울 강남구", "101호"),
                List.of(OrderItem.create(ids.generate(), null, ids.generate(), ids.generate(), ids.generate(),
                        null, ProductSnapshot.of("키링", "기본", Money.won(20_000), 2), Money.won(0))),
                CREATED_AT);
        return orders.save(order);
    }

    private record Attempt<T>(T value, RuntimeException failure) {
    }
}
