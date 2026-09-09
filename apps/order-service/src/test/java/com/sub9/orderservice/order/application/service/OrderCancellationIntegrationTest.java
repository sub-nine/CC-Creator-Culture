package com.sub9.orderservice.order.application.service;

import com.sub9.common.dto.response.ErrorResponse;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.common.security.GatewayHeaderAuthenticationFilter;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.*;
import com.sub9.orderservice.order.domain.repository.OrderQueryRepository;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
        CouponUsagePort.class,
        StockPort.class,
        PaymentCancellationPort.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("전체 주문 취소 PostgreSQL 연동")
class OrderCancellationIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-04T00:00:00Z");
    private static final UUID CUSTOMER_ID = UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private static final UUID COUPON_ID = UUID.fromString("0198f2a0-76c0-7000-8000-000000000002");
    private static final String KEY = "cancel-order";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_cancellation_test")
            .withUsername("test")
            .withPassword("test");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Autowired
    private OrderCancellationService service;

    @Autowired
    private OrderItemStatusService itemStatusService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderCommandIdempotencyService idempotencyService;

    @Autowired
    private OrderCommandJsonCodec jsonCodec;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderQueryRepository queryRepository;

    @Autowired
    private PaymentCancellationPort paymentPort;

    @Autowired
    private StockPort stockPort;

    @Autowired
    private CouponUsagePort couponUsagePort;

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

    @BeforeEach
    void preparePaymentProbe() {
        // 실제 Payment 스키마 대신 호출자의 DB 트랜잭션 참여만 확인하는 테스트 전용 기록입니다.
        jdbcTemplate.execute("create table if not exists payment_cancellation_probe (command_id uuid primary key)");
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("alter table p_orders drop constraint if exists ck_orders_test_reject_cancel");
        jdbcTemplate.update("delete from payment_cancellation_probe");
        jdbcTemplate.update("delete from p_order_command_requests");
        jdbcTemplate.update("delete from p_order_items");
        jdbcTemplate.update("delete from p_orders");
    }

    @Test
    @DisplayName("취소와 성공 결과를 저장한 뒤 재고를 복구하고 같은 요청은 결과만 재사용한다")
    void when_order_is_canceled_commit_precedes_stock_restore_and_replay_is_unchanged() {
        Order order = savedPaidOrder();
        recordPayment(false);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(restored(order).getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(commandStatus(KEY)).isEqualTo("SUCCEEDED");
            assertThat(paymentProbeCount()).isEqualTo(1);
            return null;
        }).when(stockPort).restore(eq(order.getId()), anyList(), eq(StockPort.RestoreReason.ORDER_CANCEL));

        OrderCancellationResult canceled = service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber());
        OrderCancellationResult replay = service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber());

        assertThat(canceled.httpStatus()).isEqualTo(200);
        assertThat(replay.httpStatus()).isEqualTo(200);
        assertThat(jsonCodec.encodeResponse(replay.responseBody()))
                .isEqualTo(jsonCodec.encodeResponse(canceled.responseBody()));
        JsonNode response = jsonCodec.decodeResponse(jsonCodec.encodeResponse(canceled.responseBody()));
        assertThat(response.get("data").get("orderNumber").asString()).isEqualTo(order.getOrderNumber().toString());
        assertThat(response.get("data").get("status").asString()).isEqualTo("CANCELED");
        Instant canceledAt = Instant.parse(response.get("data").get("canceledAt").asString());
        Order restored = restored(order);
        assertThat(restored.getCanceledAt())
                .isCloseTo(canceledAt, within(1, ChronoUnit.MICROS));
        assertThat(restored.getItems()).extracting(OrderItem::getStatus).containsOnly(OrderItemStatus.CANCELED);
        assertThat(restored.getItems()).extracting(OrderItem::getUserCouponId).containsOnly(COUPON_ID);
        verify(paymentPort).cancel(eq(order.getId()), any(), eq(canceledAt));
        ArgumentCaptor<List<StockPort.StockItem>> stockItems = ArgumentCaptor.captor();
        verify(stockPort).restore(eq(order.getId()), stockItems.capture(), eq(StockPort.RestoreReason.ORDER_CANCEL));
        assertThat(stockItems.getValue()).containsExactlyInAnyOrderElementsOf(order.getItems().stream()
                .map(item -> new StockPort.StockItem(item.getSkuId(), item.getProductSnapshot().getQuantity()))
                .toList());
        assertThat(jdbcTemplate.queryForObject(
                "select order_id from p_order_command_requests where idempotency_key = ?", UUID.class, KEY))
                .isEqualTo(order.getId());
        verifyNoInteractions(couponUsagePort);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Payment 또는 주문 저장 실패 시 전체 취소를 롤백하고 실패 결과를 재사용한다")
    void when_payment_or_commit_fails_cancellation_rolls_back_and_failure_is_replayed(boolean paymentFails) {
        Order order = savedPaidOrder();
        recordPayment(paymentFails);
        if (!paymentFails) {
            jdbcTemplate.execute("""
                    alter table p_orders add constraint ck_orders_test_reject_cancel check (status <> 'CANCELED')
                    """);
        }

        assertThatThrownBy(() -> service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber()))
                .isInstanceOf(RuntimeException.class);

        assertThat(restored(order).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(restored(order).getCanceledAt()).isNull();
        assertThat(restored(order).getItems()).extracting(OrderItem::getStatus).containsOnly(OrderItemStatus.ORDERED);
        assertThat(paymentProbeCount()).isZero();
        assertThat(commandStatus(KEY)).isEqualTo("FAILED");
        OrderCancellationResult replay = service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber());
        assertThat(replay.httpStatus()).isEqualTo(500);
        assertThat(jsonCodec.encodeResponse(replay.responseBody()))
                .isEqualTo(jsonCodec.encodeResponse(ErrorResponse.from(CommonErrorCode.INTERNAL_SERVER_ERROR)));
        verify(paymentPort, times(1)).cancel(eq(order.getId()), any(), any());
        verifyNoInteractions(stockPort, couponUsagePort);
    }

    @Test
    @DisplayName("재고 복구 실패는 로그로 남기고 반복 요청에서도 확정된 취소 성공을 유지한다")
    void when_stock_restore_fails_success_is_preserved_and_failure_is_logged(CapturedOutput output) {
        Order order = savedPaidOrder();
        recordPayment(false);
        doThrow(new IllegalStateException("Product 호출 실패"))
                .when(stockPort).restore(any(), anyList(), any());

        OrderCancellationResult result = service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber());
        OrderCancellationResult replay = service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber());

        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(replay.httpStatus()).isEqualTo(200);
        assertThat(restored(order).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(commandStatus(KEY)).isEqualTo("SUCCEEDED");
        assertThat(paymentProbeCount()).isEqualTo(1);
        assertThat(output.getAll()).contains("주문 취소 후 재고 복구에 실패했습니다.",
                "orderId=" + order.getId(), "reason=ORDER_CANCEL");
        verify(stockPort).restore(eq(order.getId()), anyList(), eq(StockPort.RestoreReason.ORDER_CANCEL));
        verify(paymentPort).cancel(eq(order.getId()), any(), any());
        verifyNoInteractions(couponUsagePort);
    }

    @Test
    @DisplayName("같은 키로 다른 주문을 취소할 수 없고 다른 키로 이미 취소된 주문을 다시 처리하지 않는다")
    void when_key_or_order_is_reused_no_second_cancellation_occurs() {
        Order order = savedPaidOrder();
        Order other = savedPaidOrder();
        service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber());

        assertOrderError(() -> service.cancel(CUSTOMER_ID, KEY, other.getOrderNumber()),
                OrderErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertOrderError(() -> service.cancel(CUSTOMER_ID, "another-key", order.getOrderNumber()),
                OrderErrorCode.INVALID_ORDER_STATUS);

        assertThat(restored(other).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(commandStatus(KEY)).isEqualTo("SUCCEEDED");
        assertThat(commandStatus("another-key")).isEqualTo("FAILED");
        verify(paymentPort, times(1)).cancel(any(), any(), any());
        verify(stockPort, times(1)).restore(any(), anyList(), any());
        verifyNoInteractions(couponUsagePort);
    }

    @Test
    @DisplayName("처리 중인 취소 명령은 상태와 외부 자원을 변경하지 않는다")
    void when_cancellation_is_processing_duplicate_is_rejected() {
        Order order = savedPaidOrder();
        idempotencyService.acquire(CUSTOMER_ID, OrderCommandType.CANCEL_ORDER, KEY,
                Map.of("orderNumber", order.getOrderNumber().toString()));

        assertOrderError(() -> service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber()),
                OrderErrorCode.ORDER_REQUEST_IN_PROGRESS);

        assertThat(commandStatus(KEY)).isEqualTo("PROCESSING");
        assertThat(restored(order).getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(paymentPort, stockPort, couponUsagePort);
    }

    @Test
    @DisplayName("타인 주문 취소 실패를 재사용하며 주문 소유자의 취소에는 영향을 주지 않는다")
    void when_another_customer_cancels_access_failure_is_replayed_and_owner_can_cancel() {
        Order order = savedPaidOrder();
        UUID otherCustomer = uuidGenerator.generate();

        assertOrderError(() -> service.cancel(otherCustomer, KEY, order.getOrderNumber()),
                OrderErrorCode.ORDER_ACCESS_DENIED);
        OrderCancellationResult replay = service.cancel(otherCustomer, KEY, order.getOrderNumber());

        assertThat(replay.httpStatus()).isEqualTo(403);
        verifyNoInteractions(paymentPort, stockPort, couponUsagePort);
        assertThat(service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber()).httpStatus()).isEqualTo(200);
        assertThat(restored(order).getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("없는 주문 취소는 실패 결과를 저장하고 재사용한다")
    void when_order_is_missing_not_found_is_saved_and_replayed() {
        OrderNumber missing = OrderNumber.issue(uuidGenerator.generate());

        assertOrderError(() -> service.cancel(CUSTOMER_ID, KEY, missing), OrderErrorCode.ORDER_NOT_FOUND);

        assertThat(service.cancel(CUSTOMER_ID, KEY, missing).httpStatus()).isEqualTo(404);
        assertThat(commandStatus(KEY)).isEqualTo("FAILED");
        verifyNoInteractions(paymentPort, stockPort, couponUsagePort);
    }

    @Test
    @DisplayName("외부 트랜잭션 안에서 취소를 호출하면 커밋 전 재고 복구를 막기 위해 거부한다")
    void when_called_inside_transaction_cancellation_is_rejected_before_acquiring_command() {
        Order order = savedPaidOrder();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber())))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from p_order_command_requests", Integer.class)).isZero();
        assertThat(restored(order).getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(paymentPort, stockPort, couponUsagePort);
    }

    @Test
    @DisplayName("취소 API의 최초 응답과 저장된 재응답이 일치하고 협업 기능은 한 번만 호출한다")
    void when_http_cancellation_is_repeated_saved_response_is_returned() throws Exception {
        Order order = savedPaidOrder();
        String firstResponse = mockMvc.perform(cancelRequest(CUSTOMER_ID, order)
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value(order.getOrderNumber().toString()))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.canceledAt").isString())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(cancelRequest(CUSTOMER_ID, order).header("Idempotency-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(content().json(firstResponse));

        assertThat(jsonCodec.decodeResponse(firstResponse)).isEqualTo(jsonCodec.decodeResponse(
                jdbcTemplate.queryForObject("select response_payload::text from p_order_command_requests",
                        String.class)));
        verify(paymentPort).cancel(eq(order.getId()), any(), any());
        verify(stockPort).restore(eq(order.getId()), anyList(), eq(StockPort.RestoreReason.ORDER_CANCEL));
        verifyNoInteractions(couponUsagePort);
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    @DisplayName("취소 API의 잘못된 멱등 키는 명령 저장과 협업 호출 전에 거부한다")
    void when_http_key_is_invalid_validation_rejects_before_any_changes(String key) throws Exception {
        Order order = savedPaidOrder();

        mockMvc.perform(cancelRequest(CUSTOMER_ID, order).header("Idempotency-Key", key))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.VALIDATION_ERROR.code()));

        assertThat(jdbcTemplate.queryForObject("select count(*) from p_order_command_requests", Integer.class)).isZero();
        assertThat(restored(order).getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(paymentPort, stockPort, couponUsagePort);
    }

    @Test
    @DisplayName("타인 주문 취소는 403으로 응답하고 같은 요청에도 실패 결과를 재사용한다")
    void when_http_customer_does_not_own_order_forbidden_is_replayed() throws Exception {
        Order order = savedPaidOrder();
        UUID anotherCustomer = uuidGenerator.generate();
        String firstResponse = mockMvc.perform(cancelRequest(anotherCustomer, order)
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(OrderErrorCode.ORDER_ACCESS_DENIED.code()))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(cancelRequest(anotherCustomer, order).header("Idempotency-Key", KEY))
                .andExpect(status().isForbidden())
                .andExpect(content().json(firstResponse));

        assertThat(restored(order).getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(paymentPort, stockPort, couponUsagePort);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("같거나 다른 키의 동시 취소가 주문과 협업 기능을 한 번만 변경한다")
    void when_cancellations_overlap_only_one_changes_order_and_collaborators(boolean sameKey) throws Exception {
        Order order = savedPaidOrder();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        pauseCancellation(firstLocked, releaseFirst);
        String secondKey = sameKey ? KEY : "another-key";

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<OrderCancellationResult> first = executor.submit(() ->
                        service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber()));
                assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> second = executor.submit(() -> assertOrderError(
                        () -> service.cancel(CUSTOMER_ID, secondKey, order.getOrderNumber()),
                        sameKey ? OrderErrorCode.ORDER_REQUEST_IN_PROGRESS : OrderErrorCode.INVALID_ORDER_STATUS));

                if (sameKey) {
                    second.get(5, TimeUnit.SECONDS);
                } else {
                    awaitOrderLock(second);
                }
                releaseFirst.countDown();
                OrderCancellationResult success = first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);

                assertThat(success.httpStatus()).isEqualTo(200);
                OrderCancellationResult replay = service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber());
                assertThat(jsonCodec.encodeResponse(replay.responseBody()))
                        .isEqualTo(jsonCodec.encodeResponse(success.responseBody()));
            } finally {
                releaseFirst.countDown();
            }
        }

        Order canceled = restored(order);
        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceled.getCanceledAt()).isNotNull();
        assertThat(canceled.getItems()).extracting(OrderItem::getStatus).containsOnly(OrderItemStatus.CANCELED);
        assertThat(jdbcTemplate.queryForList("select status from p_order_command_requests", String.class))
                .containsExactlyInAnyOrderElementsOf(sameKey ? List.of("SUCCEEDED") : List.of("SUCCEEDED", "FAILED"));
        verify(paymentPort).cancel(eq(order.getId()), any(), any());
        verify(stockPort).restore(eq(order.getId()), anyList(), eq(StockPort.RestoreReason.ORDER_CANCEL));
        verifyNoInteractions(couponUsagePort);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("전체 취소와 배송 준비가 경쟁하면 먼저 잠근 요청만 반영한다")
    void when_cancellation_races_preparing_only_lock_winner_changes_order(boolean cancelFirst) throws Exception {
        Order order = savedPaidOrder();
        OrderItem target = order.getItems().getFirst();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        if (cancelFirst) {
            pauseCancellation(firstLocked, releaseFirst);
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<?> first = executor.submit(() -> {
                    if (cancelFirst) {
                        assertThat(service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber()).httpStatus()).isEqualTo(200);
                    } else {
                        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                            itemStatusService.update(target.getCreatorId(), target.getId(), OrderItemStatus.PREPARING);
                            firstLocked.countDown();
                            await(releaseFirst);
                        });
                    }
                });
                assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> second = executor.submit(() -> {
                    if (cancelFirst) {
                        assertOrderError(() -> itemStatusService.update(
                                target.getCreatorId(), target.getId(), OrderItemStatus.PREPARING),
                                OrderErrorCode.INVALID_ORDER_STATUS);
                    } else {
                        assertOrderError(() -> service.cancel(CUSTOMER_ID, KEY, order.getOrderNumber()),
                                OrderErrorCode.CANNOT_CANCEL_ORDER_IN_PROGRESS);
                    }
                });
                awaitOrderLock(second);
                releaseFirst.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            } finally {
                releaseFirst.countDown();
            }
        }

        Order result = restored(order);
        if (cancelFirst) {
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(result.getCanceledAt()).isNotNull();
            assertThat(result.getItems()).extracting(OrderItem::getStatus).containsOnly(OrderItemStatus.CANCELED);
            assertThat(commandStatus(KEY)).isEqualTo("SUCCEEDED");
            verify(paymentPort).cancel(eq(order.getId()), any(), any());
            verify(stockPort).restore(eq(order.getId()), anyList(), eq(StockPort.RestoreReason.ORDER_CANCEL));
        } else {
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PROCESSING);
            assertThat(result.getCanceledAt()).isNull();
            assertThat(result.getItems()).filteredOn(item -> item.getId().equals(target.getId()))
                    .extracting(OrderItem::getStatus).containsExactly(OrderItemStatus.PREPARING);
            assertThat(result.getItems()).filteredOn(item -> !item.getId().equals(target.getId()))
                    .extracting(OrderItem::getStatus).containsOnly(OrderItemStatus.ORDERED);
            assertThat(commandStatus(KEY)).isEqualTo("FAILED");
            verifyNoInteractions(paymentPort, stockPort);
        }
        verifyNoInteractions(couponUsagePort);
    }

    private void pauseCancellation(CountDownLatch locked, CountDownLatch release) {
        doAnswer(invocation -> {
            locked.countDown();
            await(release);
            return null;
        }).when(paymentPort).cancel(any(), any(), any());
    }

    private void awaitOrderLock(Future<?> contender) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            boolean waiting = jdbcTemplate.queryForObject("""
                    select exists (
                        select 1 from pg_stat_activity
                        where datname = current_database()
                          and wait_event_type = 'Lock'
                          and query like '%p_orders%'
                    )
                    """, Boolean.class);
            if (waiting) {
                assertThat(contender.isDone()).isFalse();
                return;
            }
            if (contender.isDone()) {
                contender.get(5, TimeUnit.SECONDS);
                throw new AssertionError("경쟁 요청이 주문 잠금을 기다리지 않고 종료되었습니다.");
            }
            Thread.sleep(10);
        }
        throw new AssertionError("PostgreSQL의 주문 잠금 대기를 확인하지 못했습니다.");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("취소 경쟁 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("취소 경쟁 테스트가 중단되었습니다.", exception);
        }
    }

    private MockHttpServletRequestBuilder cancelRequest(UUID customerId, Order order) {
        return post("/api/v1/orders/{orderNumber}/cancel", order.getOrderNumber().toString())
                .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, customerId)
                .header(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, "CUSTOMER");
    }

    private static Stream<String> invalidKeys() {
        return Stream.of("", " ", "a".repeat(101), "취소요청");
    }

    private void recordPayment(boolean fail) {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            UUID commandId = invocation.getArgument(1);
            jdbcTemplate.update("insert into payment_cancellation_probe (command_id) values (?)", commandId);
            if (fail) {
                throw new IllegalStateException("결제 취소 저장 실패");
            }
            return null;
        }).when(paymentPort).cancel(any(), any(), any());
    }

    private Order savedPaidOrder() {
        Order order = Order.create(uuidGenerator.generate(), CUSTOMER_ID,
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울시 강남구", "101호"),
                List.of(item(2), item(1)), CREATED_AT);
        order.markPaid(CREATED_AT.plusSeconds(60));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> orderRepository.save(order));
        return order;
    }

    private OrderItem item(int quantity) {
        return OrderItem.create(uuidGenerator.generate(), uuidGenerator.generate(), uuidGenerator.generate(),
                uuidGenerator.generate(), COUPON_ID,
                ProductSnapshot.of("상품", "옵션", Money.won(10_000), quantity), Money.won(1_000));
    }

    private Order restored(Order order) {
        return queryRepository.findDetailByOrderNumber(order.getOrderNumber()).orElseThrow();
    }

    private String commandStatus(String key) {
        return jdbcTemplate.queryForObject(
                "select status from p_order_command_requests where idempotency_key = ?", String.class, key);
    }

    private int paymentProbeCount() {
        return jdbcTemplate.queryForObject("select count(*) from payment_cancellation_probe", Integer.class);
    }

    private static void assertOrderError(Runnable action, OrderErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
