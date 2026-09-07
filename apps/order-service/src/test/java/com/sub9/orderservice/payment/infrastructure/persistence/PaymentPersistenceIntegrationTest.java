package com.sub9.orderservice.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.domain.model.IdempotencyKey;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderCommandRequest;
import com.sub9.orderservice.order.domain.model.OrderCommandType;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.payment.domain.model.Payment;
import com.sub9.orderservice.payment.domain.model.PaymentCancellation;
import com.sub9.orderservice.payment.domain.model.PaymentMethod;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import com.sub9.orderservice.payment.domain.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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

@Testcontainers
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=public",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'",
        "management.tracing.export.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@MockitoBean(types = {
        CartQueryService.class, CouponApplicationPort.class, CouponUsagePort.class, StockPort.class,
        PaymentCancellationPort.class
})
@DisplayName("PostgreSQL 결제 저장과 조회")
class PaymentPersistenceIntegrationTest {

    private static final Instant PROCESSED_AT = Instant.parse("2026-09-07T00:00:00.123456Z");
    private static final Instant CANCELED_AT = PROCESSED_AT.plusSeconds(60);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("payment_test").withUsername("test").withPassword("test");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager entityManager;

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
        jdbcTemplate.update("delete from public.p_payment_cancellations");
        jdbcTemplate.update("delete from public.p_payments");
        jdbcTemplate.update("delete from p_order_command_requests");
        jdbcTemplate.update("delete from p_order_items");
        jdbcTemplate.update("delete from p_orders");
    }

    @ParameterizedTest
    @CsvSource({"SUCCESS, 34200,", "FAILED, 34200, MOCK_PAYMENT_FAILED", "SUCCESS, 0,", "FAILED, 0, MOCK_PAYMENT_FAILED"})
    @DisplayName("저장한 결제의 금액과 결과, UTC 기준 처리 시각이 그대로 조회된다")
    void when_payment_is_saved_original_result_is_restored(PaymentStatus status, long amount, String failureCode) {
        Payment original = savePayment(status, amount);

        transaction().executeWithoutResult(ignored -> {
            Payment restored = entityManager.find(Payment.class, original.getId());
            assertThat(restored.getOrderId()).isEqualTo(original.getOrderId());
            assertThat(restored.getMethod()).isEqualTo(PaymentMethod.MOCK);
            assertThat(restored.getStatus()).isEqualTo(status);
            assertThat(restored.getAmount()).isEqualTo(Money.won(amount));
            assertThat(restored.getFailureCode()).isEqualTo(failureCode);
            assertThat(restored.getProcessedAt()).isEqualTo(PROCESSED_AT);
            assertThat(restored.getCreatedAt()).isNotNull();
            assertThat(restored.getUpdatedAt()).isNotNull();
            assertThat(restored.getCancellation()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(longs = {34_200, 0})
    @DisplayName("저장한 결제를 전액 취소하면 결제 당시의 기록과 취소 내역이 함께 조회된다")
    void when_persisted_payment_is_canceled_original_result_and_cancellation_are_restored(long amount) {
        Payment original = savePayment(PaymentStatus.SUCCESS, amount);
        UUID commandId = saveCommand();
        UUID cancellationId = uuidGenerator.generate();
        transaction().executeWithoutResult(ignored -> {
            Payment payment = entityManager.find(Payment.class, original.getId());
            payment.cancel(cancellationId, commandId, CANCELED_AT);
        });

        transaction().executeWithoutResult(ignored -> {
            Payment restored = entityManager.find(Payment.class, original.getId());
            PaymentCancellation cancellation = restored.getCancellation();
            assertThat(restored.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(restored.getMethod()).isEqualTo(PaymentMethod.MOCK);
            assertThat(restored.getAmount()).isEqualTo(Money.won(amount));
            assertThat(restored.getFailureCode()).isNull();
            assertThat(restored.getProcessedAt()).isEqualTo(PROCESSED_AT);
            assertThat(cancellation.getId()).isEqualTo(cancellationId);
            assertThat(cancellation.getPaymentId()).isEqualTo(original.getId());
            assertThat(cancellation.getCommandRequestId()).isEqualTo(commandId);
            assertThat(cancellation.getAmount()).isEqualTo(restored.getAmount());
            assertThat(cancellation.getReasonCode()).isEqualTo("CUSTOMER_REQUEST");
            assertThat(cancellation.getCanceledAt()).isEqualTo(CANCELED_AT);
            assertThat(cancellation.getCreatedAt()).isNotNull();
            assertThat(cancellation.getUpdatedAt()).isNotNull();
        });
    }

    @ParameterizedTest
    @CsvSource({"p_payments, processed_at", "p_payment_cancellations, canceled_at"})
    @DisplayName("결제 처리와 취소 시각은 시간대가 있는 컬럼으로 저장된다")
    void when_schema_is_created_payment_times_use_timestamp_with_time_zone(String table, String column) {
        assertThat(jdbcTemplate.queryForObject("""
                select data_type from information_schema.columns
                 where table_schema = 'public' and table_name = ? and column_name = ?
                """, String.class, table, column)).isEqualTo("timestamp with time zone");
    }

    @Test
    @DisplayName("해당 주문이나 결제의 기록이 없으면 조회 결과가 비어 있다")
    void when_payment_does_not_exist_queries_return_empty() {
        assertThat(paymentRepository.findByOrderId(uuidGenerator.generate())).isEmpty();
        assertThat(paymentRepository.findById(uuidGenerator.generate())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("주문 ID와 결제 ID로 아직 취소하지 않은 결제를 조회할 수 있다")
    void when_uncanceled_payment_is_queried_payment_without_cancellation_is_returned(boolean byOrderId) {
        Payment original = savePayment(PaymentStatus.SUCCESS, 34_200);

        Payment restored = (byOrderId ? paymentRepository.findByOrderId(original.getOrderId())
                : paymentRepository.findById(original.getId())).orElseThrow();

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getOrderId()).isEqualTo(original.getOrderId());
        assertThat(restored.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(restored.getCancellation()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("취소 내역을 여러 번 저장해도 중복 없이 결제와 함께 조회된다")
    void when_detached_cancellation_is_saved_twice_both_queries_restore_one_cancellation(boolean byOrderId) {
        Payment original = savePayment(PaymentStatus.SUCCESS, 34_200);
        UUID commandId = saveCommand();
        UUID cancellationId = uuidGenerator.generate();
        original.cancel(cancellationId, commandId, CANCELED_AT);
        transaction().executeWithoutResult(ignored -> paymentRepository.save(original));
        transaction().executeWithoutResult(ignored -> paymentRepository.save(original));

        Payment restored = (byOrderId ? paymentRepository.findByOrderId(original.getOrderId())
                : paymentRepository.findById(original.getId())).orElseThrow();

        assertThat(restored.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(restored.getAmount()).isEqualTo(original.getAmount());
        assertThat(restored.getMethod()).isEqualTo(PaymentMethod.MOCK);
        assertThat(restored.getFailureCode()).isNull();
        assertThat(restored.getProcessedAt()).isEqualTo(PROCESSED_AT);
        assertThat(restored.getCancellation().getId()).isEqualTo(cancellationId);
        assertThat(restored.getCancellation().getPaymentId()).isEqualTo(original.getId());
        assertThat(restored.getCancellation().getCommandRequestId()).isEqualTo(commandId);
        assertThat(restored.getCancellation().getAmount()).isEqualTo(original.getAmount());
        assertThat(restored.getCancellation().getReasonCode()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(restored.getCancellation().getCanceledAt()).isEqualTo(CANCELED_AT);
        assertThat(jdbcTemplate.queryForObject("select count(*) from public.p_payments", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from public.p_payment_cancellations", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 주문에 결제를 중복으로 저장할 수 없다")
    void when_order_has_payment_duplicate_payment_is_rejected() {
        Payment original = savePayment(PaymentStatus.SUCCESS, 34_200);
        Payment duplicate = Payment.create(uuidGenerator.generate(), original.getOrderId(),
                original.getAmount(), PaymentStatus.SUCCESS, PROCESSED_AT);

        assertSqlState(() -> transaction().executeWithoutResult(ignored -> paymentRepository.save(duplicate)), "23505");

        assertThat(paymentRepository.findByOrderId(original.getOrderId()).orElseThrow().getId()).isEqualTo(original.getId());
        assertThat(jdbcTemplate.queryForObject("select count(*) from public.p_payments", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("같은 결제나 취소 명령으로 취소 기록을 중복 저장할 수 없다")
    void when_payment_or_command_has_cancellation_duplicate_cancellation_is_rejected(boolean samePayment) {
        Payment original = saveCanceledPayment();
        UUID paymentId = samePayment ? original.getId() : savePayment(PaymentStatus.SUCCESS, 34_200).getId();
        UUID commandId = samePayment ? saveCommand() : original.getCancellation().getCommandRequestId();

        assertSqlState(() -> jdbcTemplate.update("""
                insert into public.p_payment_cancellations (
                    id, payment_id, command_request_id, amount, reason_code, canceled_at, created_at, updated_at
                )
                select ?, ?, ?, amount, reason_code, canceled_at, created_at, updated_at
                  from public.p_payment_cancellations where id = ?
                """, uuidGenerator.generate(), paymentId, commandId, original.getCancellation().getId()), "23505");

        assertThat(jdbcTemplate.queryForObject("select count(*) from public.p_payment_cancellations", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"p_payments, order_id", "p_payment_cancellations, payment_id", "p_payment_cancellations, command_request_id"})
    @DisplayName("존재하지 않는 주문, 결제 또는 취소 명령을 참조할 수 없다")
    void when_reference_does_not_exist_foreign_key_rejects_write(String table, String column) {
        Payment payment = saveCanceledPayment();
        UUID id = table.equals("p_payments") ? payment.getId() : payment.getCancellation().getId();

        assertSqlState(() -> jdbcTemplate.update("update public." + table + " set " + column + " = ? where id = ?",
                uuidGenerator.generate(), id), "23503");
    }

    @Test
    @DisplayName("저장되지 않은 명령으로 결제를 취소하면 취소 내역이 저장되지 않는다")
    void when_cancellation_command_is_missing_transaction_preserves_uncanceled_payment() {
        Payment original = savePayment(PaymentStatus.SUCCESS, 34_200);
        original.cancel(uuidGenerator.generate(), uuidGenerator.generate(), CANCELED_AT);

        assertSqlState(() -> transaction().executeWithoutResult(ignored -> paymentRepository.save(original)), "23503");

        Payment restored = paymentRepository.findById(original.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(restored.getCancellation()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "amount = -1", "method = 'CARD'", "status = 'PENDING'",
            "failure_code = 'MOCK_PAYMENT_FAILED'", "failure_code = ''",
            "status = 'FAILED', failure_code = null", "status = 'FAILED', failure_code = ''",
            "status = 'FAILED', failure_code = 'OTHER'"
    })
    @DisplayName("결제 금액이나 방식이 잘못되거나 상태와 실패 코드가 맞지 않으면 저장할 수 없다")
    void when_payment_values_are_invalid_check_rejects_write(String assignment) {
        Payment payment = savePayment(PaymentStatus.SUCCESS, 34_200);

        assertSqlState(() -> jdbcTemplate.update("update public.p_payments set " + assignment + " where id = ?",
                payment.getId()), "23514");
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount = -1", "reason_code = 'OTHER'"})
    @DisplayName("취소 금액이 음수이거나 취소 사유가 올바르지 않으면 저장할 수 없다")
    void when_cancellation_values_are_invalid_check_rejects_write(String assignment) {
        Payment payment = saveCanceledPayment();

        assertSqlState(() -> jdbcTemplate.update("update public.p_payment_cancellations set " + assignment + " where id = ?",
                payment.getCancellation().getId()), "23514");
    }

    @ParameterizedTest
    @CsvSource({
            "p_payments, id", "p_payments, order_id", "p_payments, method", "p_payments, amount",
            "p_payments, status", "p_payments, processed_at", "p_payments, created_at", "p_payments, updated_at",
            "p_payment_cancellations, id", "p_payment_cancellations, payment_id",
            "p_payment_cancellations, command_request_id", "p_payment_cancellations, amount",
            "p_payment_cancellations, reason_code", "p_payment_cancellations, canceled_at",
            "p_payment_cancellations, created_at", "p_payment_cancellations, updated_at"
    })
    @DisplayName("필수값이 빠진 결제나 취소 기록은 저장할 수 없다")
    void when_required_value_is_null_not_null_rejects_write(String table, String column) {
        Payment payment = saveCanceledPayment();
        UUID id = table.equals("p_payments") ? payment.getId() : payment.getCancellation().getId();

        assertSqlState(() -> jdbcTemplate.update("update public." + table + " set " + column + " = null where id = ?",
                id), "23502");
    }

    @Test
    void when_database_timezone_changes_payment_instants_are_preserved() {
        Payment payment = saveCanceledPayment();
        assertThat(jdbcTemplate.queryForList("""
                select data_type from information_schema.columns
                where table_schema = 'public'
                  and (table_name = 'p_payments' and column_name = 'processed_at'
                    or table_name = 'p_payment_cancellations' and column_name = 'canceled_at')
                """, String.class)).containsExactlyInAnyOrder(
                        "timestamp with time zone", "timestamp with time zone");
        transaction().executeWithoutResult(ignored -> {
            jdbcTemplate.execute("SET LOCAL TIME ZONE 'Asia/Seoul'");
            Payment restored = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(restored.getProcessedAt()).isEqualTo(PROCESSED_AT);
            assertThat(restored.getCancellation().getCanceledAt()).isEqualTo(CANCELED_AT);
        });
    }

    private static void assertSqlState(Runnable write, String expectedState) {
        assertThatThrownBy(write::run).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().isInstanceOfSatisfying(SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo(expectedState));
    }

    private Payment saveCanceledPayment() {
        Payment payment = savePayment(PaymentStatus.SUCCESS, 34_200);
        payment.cancel(uuidGenerator.generate(), saveCommand(), CANCELED_AT);
        return transaction().execute(ignored -> paymentRepository.save(payment));
    }

    private Payment savePayment(PaymentStatus status, long amount) {
        return transaction().execute(ignored -> {
            OrderItem item = OrderItem.create(uuidGenerator.generate(), uuidGenerator.generate(),
                    uuidGenerator.generate(), uuidGenerator.generate(), null,
                    ProductSnapshot.of("아크릴 스탠드", "기본", Money.won(amount), 1), Money.won(0));
            Order order = Order.create(uuidGenerator.generate(), uuidGenerator.generate(),
                    ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울시 강남구", "101호"),
                    List.of(item), PROCESSED_AT.minusSeconds(30));
            entityManager.persist(order);
            Payment payment = Payment.create(uuidGenerator.generate(), order.getId(),
                    order.getPaymentAmount(), status, PROCESSED_AT);
            return paymentRepository.save(payment);
        });
    }

    private UUID saveCommand() {
        return transaction().execute(ignored -> {
            UUID id = uuidGenerator.generate();
            entityManager.persist(OrderCommandRequest.start(id, uuidGenerator.generate(),
                    OrderCommandType.CANCEL_ORDER, IdempotencyKey.from(id.toString()), "a".repeat(64)));
            return id;
        });
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
