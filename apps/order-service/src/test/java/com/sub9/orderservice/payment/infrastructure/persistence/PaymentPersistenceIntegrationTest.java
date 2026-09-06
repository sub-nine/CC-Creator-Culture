package com.sub9.orderservice.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
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
        CartSnapshotPort.class, CouponApplicationPort.class, CouponUsagePort.class, StockPort.class
})
@DisplayName("결제 애그리거트 PostgreSQL 영속성")
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
    @DisplayName("성공과 실패 결제의 금액, 결과와 UTC 처리 시각을 복원한다")
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
    @DisplayName("저장된 결제에 전체 취소를 추가하고 원래 결제 결과와 함께 복원한다")
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

    @Test
    @DisplayName("없는 주문 ID와 결제 ID는 빈 결과를 반환한다")
    void when_payment_does_not_exist_queries_return_empty() {
        assertThat(paymentRepository.findByOrderId(uuidGenerator.generate())).isEmpty();
        assertThat(paymentRepository.findById(uuidGenerator.generate())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("주문 ID와 결제 ID로 취소 전 결제를 조회한다")
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
    @DisplayName("트랜잭션 밖에서 추가한 취소를 재저장해도 한 건만 남고 두 조회에서 함께 복원한다")
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
