package com.sub9.orderservice.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.cart.application.service.CartService;
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
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderQueryRepository;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import com.sub9.orderservice.payment.application.dto.MockPaymentResult;
import com.sub9.orderservice.payment.domain.model.Payment;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import com.sub9.orderservice.payment.infrastructure.persistence.PaymentRepositoryAdapter;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'",
        "management.tracing.export.enabled=false"
})
@MockitoBean(types = {CartService.class, CouponApplicationPort.class, PaymentCancellationPort.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("PostgreSQL 모의 결제 처리")
class MockPaymentIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-07T00:00:00Z");
    private static final Instant NOW = CREATED_AT.plusSeconds(30).plusNanos(123456789);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("mock_payment_test").withUsername("test").withPassword("test");

    private final UuidV7Generator ids = new UuidV7Generator();

    @Autowired private MockPaymentService service;
    @Autowired private OrderRepository orders;
    @Autowired private OrderQueryRepository queries;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private PaymentRepositoryAdapter payments;
    @MockitoBean private CouponUsagePort coupons;
    @MockitoBean private StockPort stock;
    @MockitoBean private Clock clock;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void cleanDatabase() {
        jdbc.update("delete from public.p_payment_cancellations");
        jdbc.update("delete from public.p_payments");
        jdbc.update("delete from p_order_items");
        jdbc.update("delete from p_orders");
    }

    @ParameterizedTest
    @CsvSource({"SUCCESS, 34200", "FAILED, 34200", "SUCCESS, 0", "FAILED, 0"})
    @DisplayName("성공과 실패 결제를 주문과 함께 저장하고 재요청에는 같은 결과를 반환한다")
    void when_payment_is_processed_order_and_payment_are_committed_once(PaymentStatus status, long amount) {
        Order order = saveOrder(amount, true);

        MockPaymentResult first = process(order, status);
        when(clock.instant()).thenReturn(order.getExpiresAt().plusSeconds(60));
        MockPaymentResult repeated = process(order, status);
        Payment saved = payments.findByOrderId(order.getId()).orElseThrow();
        Order savedOrder = queries.findDetailByOrderNumber(order.getOrderNumber()).orElseThrow();

        assertThat(repeated).isEqualTo(first);
        assertThat(first).isEqualTo(MockPaymentResult.from(saved, order.getOrderNumber()));
        assertThat(saved.getAmount()).isEqualTo(Money.won(amount));
        assertThat(saved.getProcessedAt()).isEqualTo(NOW.truncatedTo(ChronoUnit.MICROS));
        assertThat(paymentCount()).isEqualTo(1);
        if (status == PaymentStatus.SUCCESS) {
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(savedOrder.getPaidAt()).isEqualTo(saved.getProcessedAt());
            verifyNoInteractions(coupons, stock);
        } else {
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(savedOrder.getPaidAt()).isNull();
            assertThat(saved.getFailureCode()).isEqualTo("MOCK_PAYMENT_FAILED");
            verify(coupons).restore(order.getId(), List.of(order.getItems().getFirst().getUserCouponId()));
            verify(stock).restore(order.getId(), stockItems(order), RestoreReason.PAYMENT_FAILED);
            verifyNoMoreInteractions(coupons, stock);
        }
    }

    @Test
    @DisplayName("쿠폰 복구는 로컬 트랜잭션에 참여하고 재고 복구는 DB 커밋 이후에 호출한다")
    void when_payment_fails_coupon_and_stock_use_correct_transaction_boundaries() {
        Order order = saveOrder(100, true);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(coupons).restore(any(), any());
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(paymentCount()).isEqualTo(1);
            assertThat(orderStatus(order)).isEqualTo("FAILED");
            assertThat(payments.findByOrderId(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.FAILED);
            return null;
        }).when(stock).restore(any(), any(), any());

        assertThat(process(order, PaymentStatus.FAILED).status()).isEqualTo(PaymentStatus.FAILED);
        verify(coupons).restore(order.getId(), List.of(order.getItems().getFirst().getUserCouponId()));
        verify(stock).restore(order.getId(), stockItems(order), RestoreReason.PAYMENT_FAILED);
    }

    @Test
    @DisplayName("쿠폰 복구가 실패하면 주문 상태를 롤백하고 결제와 재고를 처리하지 않는다")
    void when_coupon_restore_fails_local_changes_are_rolled_back() {
        Order order = saveOrder(100, true);
        doThrow(new IllegalStateException("쿠폰 복구 실패")).when(coupons).restore(any(), any());

        assertThatThrownBy(() -> process(order, PaymentStatus.FAILED))
                .isInstanceOf(IllegalStateException.class).hasMessage("쿠폰 복구 실패");

        assertNoPayment(order);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("결제 INSERT 이후 예외가 발생하면 결제와 주문 변경을 모두 롤백한다")
    void when_payment_save_fails_after_flush_payment_and_order_are_rolled_back(PaymentStatus status) {
        Order order = saveOrder(100, true);
        doAnswer(call -> {
            call.callRealMethod();
            entityManager.flush();
            assertThat(paymentCount()).isEqualTo(1);
            throw new IllegalStateException("결제 저장 실패");
        }).when(payments).save(any(Payment.class));

        assertThatThrownBy(() -> process(order, status))
                .isInstanceOf(DataAccessException.class).hasRootCauseMessage("결제 저장 실패");

        assertNoPayment(order);
    }

    @Test
    @DisplayName("재고 복구 오류가 발생해도 실패 기록을 보존하고 재요청 시 복구를 반복하지 않는다")
    void when_stock_restore_fails_committed_failure_is_preserved() {
        Order order = saveOrder(100, false);
        doThrow(new IllegalStateException("재고 서비스 연결 실패")).when(stock).restore(any(), any(), any());

        MockPaymentResult first = process(order, PaymentStatus.FAILED);

        assertThat(process(order, PaymentStatus.FAILED)).isEqualTo(first);
        assertThat(orderStatus(order)).isEqualTo("FAILED");
        assertThat(paymentCount()).isEqualTo(1);
        verify(stock).restore(order.getId(), stockItems(order), RestoreReason.PAYMENT_FAILED);
        verifyNoMoreInteractions(stock);
        verifyNoInteractions(coupons);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("반대 결과의 재요청은 기존 결제와 주문 상태를 변경하지 않는다")
    void when_opposite_result_is_requested_committed_result_is_unchanged(PaymentStatus status) {
        Order order = saveOrder(100, false);
        MockPaymentResult original = process(order, status);
        PaymentStatus opposite = status == PaymentStatus.SUCCESS ? PaymentStatus.FAILED : PaymentStatus.SUCCESS;

        assertBusinessError(() -> process(order, opposite), OrderErrorCode.INVALID_ORDER_STATUS);

        assertThat(process(order, status)).isEqualTo(original);
        assertThat(paymentCount()).isEqualTo(1);
        assertThat(orderStatus(order)).isEqualTo(status == PaymentStatus.SUCCESS ? "PAID" : "FAILED");
    }

    @ParameterizedTest
    @CsvSource({"SUCCESS, -1", "SUCCESS, 0", "SUCCESS, 1", "FAILED, -1", "FAILED, 0", "FAILED, 1"})
    @DisplayName("결제 기한 직전만 허용하고 기한에 도달하면 주문 만료 작업에 처리를 맡긴다")
    void when_payment_reaches_deadline_only_earlier_time_is_accepted(PaymentStatus status, long nanos) {
        Order order = saveOrder(100, false);
        when(clock.instant()).thenReturn(order.getExpiresAt().plusNanos(nanos));

        if (nanos < 0) {
            assertThat(process(order, status).processedAt()).isBefore(order.getExpiresAt());
        } else {
            assertBusinessError(() -> process(order, status), OrderErrorCode.ORDER_ALREADY_EXPIRED);
            assertNoPayment(order);
            verifyNoInteractions(coupons);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAID", "PROCESSING", "COMPLETED", "CANCELED", "FAILED", "EXPIRED"})
    @DisplayName("결제 대기 상태가 아닌 주문에 새로운 결제 기록을 만들지 않는다")
    void when_order_is_not_pending_new_payment_is_rejected(String status) {
        Order order = saveOrder(100, false);
        boolean wasPaid = !List.of("FAILED", "EXPIRED").contains(status);
        jdbc.update("update p_orders set status = ?, paid_at = ?, canceled_at = ? where id = ?",
                status, wasPaid ? java.sql.Timestamp.from(NOW) : null,
                status.equals("CANCELED") ? java.sql.Timestamp.from(NOW) : null, order.getId());

        assertBusinessError(() -> process(order, PaymentStatus.SUCCESS), status.equals("EXPIRED")
                ? OrderErrorCode.ORDER_ALREADY_EXPIRED : OrderErrorCode.INVALID_ORDER_STATUS);

        assertThat(orderStatus(order)).isEqualTo(status);
        assertThat(paymentCount()).isZero();
        verifyNoInteractions(coupons, stock);
    }

    @Test
    @DisplayName("없는 주문과 타인 주문은 결제 기록과 자원을 변경하지 않는다")
    void when_order_is_missing_or_owned_by_another_customer_payment_is_rejected() {
        Order order = saveOrder(100, false);
        assertBusinessError(() -> service.process(order.getCustomerId(), OrderNumber.issue(ids.generate()),
                PaymentStatus.SUCCESS), OrderErrorCode.ORDER_NOT_FOUND);
        assertBusinessError(() -> service.process(ids.generate(), order.getOrderNumber(), PaymentStatus.SUCCESS),
                OrderErrorCode.ORDER_ACCESS_DENIED);
        assertNoPayment(order);
        verifyNoInteractions(coupons);
    }

    @Test
    @DisplayName("외부 트랜잭션 안에서 결제를 호출하면 처리를 시작하지 않는다")
    void when_caller_has_transaction_payment_entry_point_rejects_it() {
        Order order = saveOrder(100, false);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(ignored ->
                process(order, PaymentStatus.FAILED))).isInstanceOf(IllegalTransactionStateException.class);
        assertNoPayment(order);
        verifyNoInteractions(coupons);
    }

    private MockPaymentResult process(Order order, PaymentStatus status) {
        return service.process(order.getCustomerId(), order.getOrderNumber(), status);
    }

    private void assertNoPayment(Order order) {
        assertThat(paymentCount()).isZero();
        assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(queries.findDetailByOrderNumber(order.getOrderNumber()).orElseThrow().getPaidAt()).isNull();
        verifyNoInteractions(stock);
    }

    private void assertBusinessError(Runnable action, OrderErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(expected));
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

    private Order saveOrder(long amount, boolean withCoupon) {
        Order order = Order.create(ids.generate(), ids.generate(),
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울 강남구", "101호"),
                List.of(OrderItem.create(ids.generate(), ids.generate(), ids.generate(), ids.generate(),
                        withCoupon ? ids.generate() : null,
                        ProductSnapshot.of("키링", "기본", Money.won(20_000), 2), Money.won(40_000 - amount))),
                CREATED_AT);
        orders.save(order);
        return order;
    }
}
