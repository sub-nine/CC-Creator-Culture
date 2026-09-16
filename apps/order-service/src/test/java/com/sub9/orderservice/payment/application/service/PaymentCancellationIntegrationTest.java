package com.sub9.orderservice.payment.application.service;

import static com.sub9.orderservice.support.PostgresConcurrencySupport.await;
import static com.sub9.orderservice.support.PostgresConcurrencySupport.awaitOrderLock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.concurrent.CompletableFuture;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.common.security.GatewayHeaderAuthenticationFilter;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.service.OrderCommandIdempotencyService;
import com.sub9.orderservice.order.application.service.OrderItemStatusService;
import com.sub9.orderservice.order.domain.model.IdempotencyKey;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderCommandRequest;
import com.sub9.orderservice.order.domain.model.OrderCommandType;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderItemStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.payment.domain.model.Payment;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import com.sub9.orderservice.payment.infrastructure.persistence.PaymentRepositoryAdapter;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'",
        "management.tracing.export.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@MockitoBean(types = {CartQueryService.class, CouponApplicationPort.class})
class PaymentCancellationIntegrationTest {

    private static final Instant PAID_AT = Instant.parse("2026-09-07T00:00:00.123456Z");
    private static final UUID CUSTOMER = UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private final UuidV7Generator ids = new UuidV7Generator();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("cancellation_test").withUsername("test").withPassword("test");

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PaymentCancellationService cancellationService;
    @Autowired private OrderItemStatusService itemStatusService;
    @Autowired private ObjectMapper mapper;
    @MockitoSpyBean private PaymentRepositoryAdapter payments;
    @MockitoSpyBean private OrderCommandIdempotencyService commands;
    @MockitoBean private StockPort stock;
    @MockitoBean private KafkaTemplate<String, String> kafka;
    @MockitoSpyBean private CouponUsagePort couponUsage;

    @BeforeEach
    void setKafkaResult() {
        org.mockito.Mockito.clearInvocations(kafka);
        org.mockito.Mockito.when(kafka.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("p_payment_cancellations", "p_payments", "p_order_command_requests",
                "p_order_items", "p_orders", "p_user_coupons", "p_coupons")) {
            jdbc.update("delete from " + table);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 10000})
    void when_paid_order_is_canceled_full_amount_is_saved_once(long amount) throws Exception {
        Order order = paidOrder(amount, PaymentStatus.SUCCESS);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertState("CANCELED", "CANCELED", 1, "SUCCEEDED");
            return null;
        }).when(stock).restore(eq(order.getId()), anyList(), eq(StockPort.RestoreReason.ORDER_CANCEL));

        String first = mvc.perform(request(order, "cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("CANCELED"))
                .andReturn().getResponse().getContentAsString();
        String replay = mvc.perform(request(order, "cancel")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(replay)).isEqualTo(mapper.readTree(first));
        assertState("CANCELED", "CANCELED", 1, "SUCCEEDED");
        transaction().executeWithoutResult(ignored -> {
            Payment payment = payments.findByOrderId(order.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getAmount()).isEqualTo(Money.won(amount));
            assertThat(payment.getProcessedAt()).isEqualTo(PAID_AT);
            var cancellation = payment.getCancellation();
            assertThat(cancellation.getId().version()).isEqualTo(7);
            assertThat(cancellation.getAmount()).isEqualTo(payment.getAmount());
            assertThat(cancellation.getReasonCode()).isEqualTo("CUSTOMER_REQUEST");
            assertThat(cancellation.getCanceledAt()).isEqualTo(em.find(Order.class, order.getId()).getCanceledAt());
            assertThat(cancellation.getCommandRequestId()).isEqualTo(jdbc.queryForObject(
                    "select id from p_order_command_requests where idempotency_key = 'cancel'", UUID.class));
            UserCoupon coupon = em.find(UserCoupon.class, order.getItems().getFirst().getUserCouponId());
            assertThat(coupon.getStatus()).isEqualTo(UserCouponStatus.USED);
            assertThat(coupon.getUsedAt()).isEqualTo(PAID_AT);
            assertThat(coupon.getOrderId()).isEqualTo(order.getId());
        });
        mvc.perform(request(order, "different")).andExpect(status().isConflict())
                .andExpect(jsonPath("errorCode").value("ORDER_0003"));
        assertThat(countCancellations()).isEqualTo(1);
        verify(stock).restore(eq(order.getId()), anyList(), eq(StockPort.RestoreReason.ORDER_CANCEL));
        verifyNoInteractions(couponUsage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "failed", "canceled"})
    void when_payment_cannot_be_canceled_order_changes_are_rolled_back(String condition) throws Exception {
        Order order = paidOrder(10000, condition.equals("failed") ? PaymentStatus.FAILED : PaymentStatus.SUCCESS);
        if (condition.equals("missing")) {
            jdbc.update("delete from p_payments");
        } else if (condition.equals("canceled")) {
            transaction().executeWithoutResult(ignored -> {
                UUID commandId = ids.generate();
                em.persist(OrderCommandRequest.start(commandId, CUSTOMER, OrderCommandType.CANCEL_ORDER,
                        IdempotencyKey.from("previous"), "a".repeat(64)));
                cancellationService.cancel(order.getId(), commandId, PAID_AT.plusSeconds(60));
            });
        }
        mvc.perform(request(order, "cancel")).andExpect(status().isConflict())
                .andExpect(jsonPath("errorCode").value("PAYMENT_0002"));
        assertState("PAID", "ORDERED", condition.equals("canceled") ? 1 : 0, "FAILED");
        verifyNoInteractions(stock, couponUsage);
    }

    @Test
    void when_shipping_has_started_payment_is_unchanged() throws Exception {
        Order order = paidOrder(10000, PaymentStatus.SUCCESS);
        OrderItem item = order.getItems().getFirst();
        itemStatusService.update(item.getCreatorId(), item.getId(), OrderItemStatus.PREPARING);
        mvc.perform(request(order, "cancel")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("errorCode").value("ORDER_0005"));
        assertThat(countCancellations()).isZero();
        assertThat(jdbc.queryForObject("select status from p_payments", String.class)).isEqualTo("SUCCESS");
        verifyNoInteractions(stock, couponUsage);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void when_save_fails_entire_cancellation_rolls_back(boolean failPaymentSave) throws Exception {
        Order order = paidOrder(10000, PaymentStatus.SUCCESS);
        if (failPaymentSave) {
            doAnswer(invocation -> {
                invocation.callRealMethod();
                em.flush();
                throw new IllegalStateException("결제 취소 저장 실패");
            }).when(payments).save(any());
        } else {
            doAnswer(invocation -> {
                invocation.callRealMethod();
                em.flush();
                throw new IllegalStateException("명령 성공 결과 저장 실패");
            }).when(AopTestUtils.<OrderCommandIdempotencyService>getUltimateTargetObject(commands))
                    .completeSuccess(any(), any(), anyInt(), any());
        }
        mvc.perform(request(order, "cancel")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("errorCode").value("COMMON_0001"));
        assertState("PAID", "ORDERED", 0, "FAILED");
        assertThat(jdbc.queryForObject("select status from p_payments", String.class)).isEqualTo("SUCCESS");
        verifyNoInteractions(stock, couponUsage);
    }

    @Test
    void when_no_transaction_exists_cancellation_is_rejected_before_saving() {
        Order order = paidOrder(10000, PaymentStatus.SUCCESS);
        assertThatThrownBy(() -> cancellationService.cancel(order.getId(), ids.generate(), PAID_AT))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(countCancellations()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("같거나 다른 멱등 키의 동시 취소는 실제 취소 행과 재고 복구를 한 번만 만든다")
    void when_cancellations_overlap_one_cancellation_is_persisted(boolean sameKey) throws Exception {
        Order order = paidOrder(10000, PaymentStatus.SUCCESS);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        pauseCancellation(order, locked, release);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<String> first = executor.submit(() -> mvc.perform(request(order, "cancel"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> second = executor.submit(() -> mvc.perform(request(order, sameKey ? "cancel" : "other"))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("errorCode").value(sameKey ? "ORDER_0008" : "ORDER_0003")));
                if (sameKey) second.get(5, TimeUnit.SECONDS);
                else awaitOrderLock(jdbc, second);
                release.countDown();
                String firstBody = first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
                String replay = mvc.perform(request(order, "cancel")).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
                assertThat(mapper.readTree(replay)).isEqualTo(mapper.readTree(firstBody));
            } finally {
                release.countDown();
            }
        }
        assertState("CANCELED", "CANCELED", 1, "SUCCEEDED");
        assertPaymentAndCoupon(order, true);
        verify(stock).restore(eq(order.getId()), anyList(), eq(StockPort.RestoreReason.ORDER_CANCEL));
        verifyNoMoreInteractions(stock);
        verifyNoInteractions(couponUsage);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("취소와 배송 준비의 경쟁에서 선점한 상태와 실제 결제 취소 기록만 남는다")
    void when_cancellation_races_preparing_only_winner_is_persisted(boolean cancelFirst) throws Exception {
        Order order = paidOrder(10000, PaymentStatus.SUCCESS);
        OrderItem item = order.getItems().getFirst();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        if (cancelFirst) pauseCancellation(order, locked, release);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<?> first = executor.submit(() -> {
                    if (cancelFirst) {
                        mvc.perform(request(order, "cancel")).andExpect(status().isOk());
                    } else {
                        transaction().executeWithoutResult(ignored -> {
                            itemStatusService.update(item.getCreatorId(), item.getId(), OrderItemStatus.PREPARING);
                            locked.countDown();
                            await(release);
                        });
                    }
                    return null;
                });
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> second = executor.submit(() -> {
                    if (cancelFirst) {
                        assertThatThrownBy(() -> itemStatusService.update(
                                item.getCreatorId(), item.getId(), OrderItemStatus.PREPARING))
                                .isInstanceOfSatisfying(BusinessException.class, error ->
                                        assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS));
                    } else {
                        mvc.perform(request(order, "cancel")).andExpect(status().isBadRequest())
                                .andExpect(jsonPath("errorCode").value("ORDER_0005"));
                    }
                    return null;
                });
                awaitOrderLock(jdbc, second);
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        if (cancelFirst) {
            assertState("CANCELED", "CANCELED", 1, "SUCCEEDED");
            verify(stock).restore(eq(order.getId()), anyList(), eq(StockPort.RestoreReason.ORDER_CANCEL));
        } else {
            assertThat(jdbc.queryForObject("select status from p_orders", String.class)).isEqualTo("PROCESSING");
            assertThat(jdbc.queryForObject("select canceled_at from p_orders", Object.class)).isNull();
            assertThat(jdbc.queryForObject("select status from p_order_items where id = ?", String.class, item.getId()))
                    .isEqualTo("PREPARING");
            assertThat(jdbc.queryForList("select status from p_order_items where id <> ?", String.class, item.getId()))
                    .containsOnly("ORDERED");
            assertThat(jdbc.queryForObject("select status from p_order_command_requests", String.class)).isEqualTo("FAILED");
        }
        assertPaymentAndCoupon(order, cancelFirst);
        verifyNoMoreInteractions(stock);
        verifyNoInteractions(couponUsage);
    }

    private void pauseCancellation(Order order, CountDownLatch locked, CountDownLatch release) {
        doAnswer(call -> {
            Object payment = call.callRealMethod();
            locked.countDown();
            await(release);
            return payment;
        }).when(payments).findByOrderId(order.getId());
    }

    private void assertPaymentAndCoupon(Order order, boolean canceled) {
        assertThat(countCancellations()).isEqualTo(canceled ? 1 : 0);
        transaction().executeWithoutResult(ignored -> {
            Payment payment = payments.findByOrderId(order.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getAmount()).isEqualTo(Money.won(10000));
            assertThat(payment.getProcessedAt()).isEqualTo(PAID_AT);
            if (canceled) {
                assertThat(payment.getCancellation().getAmount()).isEqualTo(payment.getAmount());
                assertThat(payment.getCancellation().getCanceledAt())
                        .isEqualTo(em.find(Order.class, order.getId()).getCanceledAt());
            } else {
                assertThat(payment.getCancellation()).isNull();
            }
            UserCoupon coupon = em.find(UserCoupon.class, order.getItems().getFirst().getUserCouponId());
            assertThat(coupon.getStatus()).isEqualTo(UserCouponStatus.USED);
            assertThat(coupon.getUsedAt()).isEqualTo(PAID_AT);
            assertThat(coupon.getOrderId()).isEqualTo(order.getId());
        });
    }

    private Order paidOrder(long amount, PaymentStatus paymentStatus) {
        return transaction().execute(ignored -> {
            UUID orderId = ids.generate();
            Coupon coupon = Coupon.create(ids.generate(), "취소 쿠폰", amount == 0 ? 100 : 50, 10,
                    PAID_AT.minusSeconds(3600), PAID_AT.plusSeconds(3600), CUSTOMER, PAID_AT.minusSeconds(60));
            coupon.issue(CUSTOMER, PAID_AT.minusSeconds(30));
            em.persist(coupon);
            UserCoupon userCoupon = UserCoupon.issue(ids.generate(), coupon, CUSTOMER, PAID_AT.minusSeconds(30));
            userCoupon.use(CUSTOMER, orderId, PAID_AT);
            em.persist(userCoupon);
            OrderItem first = OrderItem.create(ids.generate(), null, ids.generate(), ids.generate(), ids.generate(),
                    userCoupon.getId(), ProductSnapshot.of("상품", "옵션", Money.won(20000), 1),
                    Money.won(20000 - amount));
            OrderItem second = OrderItem.create(ids.generate(), null, ids.generate(), ids.generate(), ids.generate(),
                    null, ProductSnapshot.of("사은품", "기본", Money.won(0), 1), Money.won(0));
            Order order = Order.create(orderId, CUSTOMER,
                    ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울시 강남구", "101호"),
                    List.of(first, second), PAID_AT.minusSeconds(60));
            order.markPaid(PAID_AT);
            em.persist(order);
            payments.save(Payment.create(ids.generate(), orderId, Money.won(amount), paymentStatus, PAID_AT));
            return order;
        });
    }

    private MockHttpServletRequestBuilder request(Order order, String key) {
        return post("/api/v1/orders/{orderNumber}/cancel", order.getOrderNumber().toString())
                .header("Idempotency-Key", key)
                .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, CUSTOMER)
                .header(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, "CUSTOMER");
    }

    private void assertState(String orderStatus, String itemStatus, int cancellations, String commandStatus) {
        assertThat(jdbc.queryForObject("select status from p_orders", String.class)).isEqualTo(orderStatus);
        assertThat(jdbc.queryForList("select status from p_order_items", String.class)).containsOnly(itemStatus);
        assertThat(countCancellations()).isEqualTo(cancellations);
        assertThat(jdbc.queryForObject("select status from p_order_command_requests where idempotency_key = 'cancel'",
                String.class)).isEqualTo(commandStatus);
    }

    private int countCancellations() {
        return jdbc.queryForObject("select count(*) from p_payment_cancellations", Integer.class);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
