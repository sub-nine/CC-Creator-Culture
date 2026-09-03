package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderCommandStatus;
import com.sub9.orderservice.order.domain.model.OrderCommandType;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("주문 명령 멱등 처리 PostgreSQL 연동")
class OrderCommandIdempotencyIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-03T00:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_command_test")
            .withUsername("test")
            .withPassword("test");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Autowired
    private OrderCommandIdempotencyService idempotencyService;

    @Autowired
    private OrderRepository orderRepository;

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
        jdbcTemplate.update("delete from p_order_command_requests");
        jdbcTemplate.update("delete from p_order_items");
        jdbcTemplate.update("delete from p_orders");
    }

    @Test
    @DisplayName("멱등 명령 테이블의 타입, 제약, 인덱스와 주문 외래 키를 생성한다")
    void when_schema_is_created_expected_command_constraints_and_types_exist() {
        Set<String> constraints = Set.copyOf(jdbcTemplate.queryForList("""
                select constraint_name
                  from information_schema.table_constraints
                 where table_schema = 'public'
                   and table_name = 'p_order_command_requests'
                """, String.class));
        Set<String> indexes = Set.copyOf(jdbcTemplate.queryForList("""
                select indexname
                  from pg_indexes
                 where schemaname = 'public'
                   and tablename = 'p_order_command_requests'
                """, String.class));

        assertThat(constraints).contains(
                "uk_order_command_actor_type_key",
                "fk_order_command_requests_order",
                "ck_order_command_type",
                "ck_order_command_status",
                "ck_order_command_request_hash",
                "ck_order_command_completion");
        assertThat(indexes).contains("idx_order_command_order_id");
        assertThat(columnType("response_status", "data_type")).isEqualTo("smallint");
        assertThat(columnType("response_payload", "udt_name")).isEqualTo("jsonb");
        assertThat(columnType("request_hash", "data_type")).isEqualTo("character");
        assertThat(columnType("completed_at", "data_type"))
                .isEqualTo("timestamp without time zone");
    }

    @Test
    @DisplayName("완료 정보 조합, 요청 해시와 존재하지 않는 주문 참조를 데이터베이스가 거부한다")
    void when_database_command_invariants_are_violated_write_is_rejected() {
        OrderCommandAcquireResult.Started started = started(acquire(
                uuidGenerator.generate(), OrderCommandType.CREATE_ORDER, "constraint-key", 1));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update p_order_command_requests set response_status = 201 where id = ?",
                started.commandRequestId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update p_order_command_requests set request_hash = ? where id = ?",
                "g".repeat(64),
                started.commandRequestId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update p_order_command_requests set order_id = ? where id = ?",
                uuidGenerator.generate(),
                started.commandRequestId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 처리 중 요청은 거부하고 같은 키의 다른 요청은 재사용 충돌로 구분한다")
    void when_processing_request_is_repeated_in_progress_and_reuse_errors_are_distinguished() {
        UUID actorId = uuidGenerator.generate();
        String key = "create-order-key";

        OrderCommandAcquireResult first = acquire(actorId, OrderCommandType.CREATE_ORDER, key, 1);

        assertThat(first).isInstanceOf(OrderCommandAcquireResult.Started.class);
        assertBusinessError(
                () -> acquire(actorId, OrderCommandType.CREATE_ORDER, key, 1),
                OrderErrorCode.ORDER_REQUEST_IN_PROGRESS);
        assertBusinessError(
                () -> acquire(actorId, OrderCommandType.CREATE_ORDER, key, 2),
                OrderErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(commandCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("완료된 성공과 실패 요청은 저장한 HTTP 상태와 응답을 반환한다")
    void when_commands_are_completed_saved_success_and_failure_results_are_replayed() {
        UUID actorId = uuidGenerator.generate();
        OrderCommandAcquireResult.Started success = started(
                acquire(actorId, OrderCommandType.CREATE_ORDER, "success-key", 1));
        Order order = order();
        Map<String, Object> successBody = Map.of(
                "message", "주문 생성 성공",
                "data", Map.of("orderNumber", order.getOrderNumber().toString(),
                        "status", "PENDING_PAYMENT"));

        transaction().executeWithoutResult(status -> {
            Order savedOrder = orderRepository.save(order);
            idempotencyService.completeSuccess(success.commandRequestId(), savedOrder, 201, successBody);
        });

        OrderCommandAcquireResult.Replay successReplay = replay(
                acquire(actorId, OrderCommandType.CREATE_ORDER, "success-key", 1));
        assertThat(successReplay.httpStatus()).isEqualTo(201);
        assertThat(successReplay.responseBody().path("data").path("orderNumber").asString())
                .isEqualTo(order.getOrderNumber().toString());

        OrderCommandAcquireResult.Started failure = started(
                acquire(actorId, OrderCommandType.CANCEL_ORDER, "failure-key", 1));
        Map<String, Object> failureBody = Map.of(
                "errorCode", "ORDER_0005",
                "message", "배송 처리가 시작된 주문은 취소할 수 없습니다.",
                "errors", List.of());

        idempotencyService.completeFailure(failure.commandRequestId(), order, 400, failureBody);

        OrderCommandAcquireResult.Replay failureReplay = replay(
                acquire(actorId, OrderCommandType.CANCEL_ORDER, "failure-key", 1));
        assertThat(failureReplay.httpStatus()).isEqualTo(400);
        assertThat(failureReplay.responseBody().path("errorCode").asString())
                .isEqualTo("ORDER_0005");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                  from p_order_command_requests
                 where status in ('SUCCEEDED', 'FAILED')
                   and response_payload is not null
                   and completed_at is not null
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("요청자 또는 명령 종류가 다르면 같은 멱등 키를 독립적으로 사용한다")
    void when_actor_or_command_type_differs_same_key_has_independent_scope() {
        UUID firstActor = uuidGenerator.generate();
        UUID secondActor = uuidGenerator.generate();
        String key = "shared-key";

        assertThat(acquire(firstActor, OrderCommandType.CREATE_ORDER, key, 1))
                .isInstanceOf(OrderCommandAcquireResult.Started.class);
        assertThat(acquire(secondActor, OrderCommandType.CREATE_ORDER, key, 1))
                .isInstanceOf(OrderCommandAcquireResult.Started.class);
        assertThat(acquire(firstActor, OrderCommandType.CANCEL_ORDER, key, 1))
                .isInstanceOf(OrderCommandAcquireResult.Started.class);
        assertThat(acquire(firstActor, OrderCommandType.CREATE_ORDER, "SHARED-KEY", 1))
                .isInstanceOf(OrderCommandAcquireResult.Started.class);

        assertThat(commandCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("동시에 들어온 같은 요청 중 한 건만 처리를 시작한다")
    void when_same_request_is_concurrent_only_one_command_starts() throws Exception {
        UUID actorId = uuidGenerator.generate();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(
                    () -> concurrentAcquire(actorId, ready, start));
            Future<String> second = executor.submit(
                    () -> concurrentAcquire(actorId, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("STARTED", OrderErrorCode.ORDER_REQUEST_IN_PROGRESS.code());
            assertThat(commandCount()).isEqualTo(1);
        } finally {
            start.countDown();
        }
    }

    @Test
    @DisplayName("성공 결과는 업무 롤백에 참여하고 확정 실패 결과는 별도 트랜잭션으로 보존한다")
    void when_business_transaction_rolls_back_success_is_reverted_and_confirmed_failure_survives() {
        UUID actorId = uuidGenerator.generate();
        OrderCommandAcquireResult.Started started = started(
                acquire(actorId, OrderCommandType.CREATE_ORDER, "rollback-key", 1));
        Order order = order();

        transaction().executeWithoutResult(status -> {
            Order savedOrder = orderRepository.save(order);
            idempotencyService.completeSuccess(
                    started.commandRequestId(),
                    savedOrder,
                    201,
                    Map.of("message", "주문 생성 성공", "data", Map.of("orderNumber", "ORD-ROLLBACK")));
            status.setRollbackOnly();
        });

        assertThat(jdbcTemplate.queryForObject(
                "select status from p_order_command_requests where id = ?",
                String.class,
                started.commandRequestId())).isEqualTo(OrderCommandStatus.PROCESSING.name());
        assertThat(jdbcTemplate.queryForObject(
                "select response_payload is null from p_order_command_requests where id = ?",
                Boolean.class,
                started.commandRequestId())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_orders where id = ?",
                Integer.class,
                order.getId())).isZero();

        OrderCommandAcquireResult.Started failed = started(
                acquire(actorId, OrderCommandType.CANCEL_ORDER, "failure-rollback-key", 1));
        transaction().executeWithoutResult(status -> {
            idempotencyService.completeFailure(
                    failed.commandRequestId(),
                    null,
                    409,
                    Map.of("errorCode", "ORDER_0003", "message", "현재 주문 상태에서 처리할 수 없습니다."));
            status.setRollbackOnly();
        });
        assertThat(jdbcTemplate.queryForObject(
                "select status from p_order_command_requests where id = ?",
                String.class,
                failed.commandRequestId())).isEqualTo(OrderCommandStatus.FAILED.name());
    }

    @Test
    @DisplayName("요청 원문과 민감정보가 저장되지 않고 민감한 응답 저장은 거부한다")
    void when_sensitive_input_is_hashed_raw_values_are_not_stored_and_sensitive_response_is_rejected() {
        UUID actorId = uuidGenerator.generate();
        String secretAddress = "서울시 비밀 주소 123";
        String secretToken = "Bearer secret-token-value";
        Map<String, Object> request = Map.of(
                "shippingAddress", Map.of("addressLine1", secretAddress),
                "authorization", secretToken,
                "items", List.of(Map.of("cartItemId", uuidGenerator.generate())));
        OrderCommandAcquireResult.Started started = started(idempotencyService.acquire(
                actorId, OrderCommandType.CREATE_ORDER, "privacy-key", request));

        idempotencyService.completeFailure(
                started.commandRequestId(),
                null,
                400,
                Map.of("errorCode", "COMMON_0003", "message", "입력값 검증에 실패했습니다."));

        String storedRow = jdbcTemplate.queryForObject(
                "select row_to_json(request)::text from p_order_command_requests request where id = ?",
                String.class,
                started.commandRequestId());
        assertThat(storedRow).doesNotContain(secretAddress, secretToken, "shippingAddress", "authorization");

        OrderCommandAcquireResult.Started unsafe = started(
                acquire(actorId, OrderCommandType.CREATE_ORDER, "unsafe-response-key", 2));
        assertThatThrownBy(() -> idempotencyService.completeFailure(
                unsafe.commandRequestId(),
                null,
                400,
                Map.of("data", Map.of("accessToken", secretToken))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("민감정보");
        assertThat(jdbcTemplate.queryForObject(
                "select status from p_order_command_requests where id = ?",
                String.class,
                unsafe.commandRequestId())).isEqualTo(OrderCommandStatus.PROCESSING.name());
    }

    private OrderCommandAcquireResult acquire(
            UUID actorId, OrderCommandType commandType, String key, int value) {
        return idempotencyService.acquire(actorId, commandType, key, Map.of("value", value));
    }

    private String concurrentAcquire(UUID actorId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            OrderCommandAcquireResult result = acquire(
                    actorId, OrderCommandType.CREATE_ORDER, "concurrent-key", 1);
            return result instanceof OrderCommandAcquireResult.Started
                    ? "STARTED"
                    : "REPLAY";
        } catch (BusinessException exception) {
            return exception.getErrorCode().code();
        }
    }

    private int commandCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from p_order_command_requests", Integer.class);
    }

    private String columnType(String columnName, String typeColumn) {
        return jdbcTemplate.queryForObject("""
                select %s
                  from information_schema.columns
                 where table_schema = 'public'
                   and table_name = 'p_order_command_requests'
                   and column_name = ?
                """.formatted(typeColumn), String.class, columnName);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private Order order() {
        OrderItem item = OrderItem.create(
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                null,
                ProductSnapshot.of("아크릴 스탠드", "A 타입", Money.won(18_000), 2),
                Money.won(1_800));
        return Order.create(
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울시 강남구", "101호"),
                List.of(item),
                CREATED_AT);
    }

    private static OrderCommandAcquireResult.Started started(OrderCommandAcquireResult result) {
        return (OrderCommandAcquireResult.Started) result;
    }

    private static OrderCommandAcquireResult.Replay replay(OrderCommandAcquireResult result) {
        return (OrderCommandAcquireResult.Replay) result;
    }

    private static void assertBusinessError(Runnable action, OrderErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
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
}
