package com.sub9.orderservice.order.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.infrastructure.adapter.CartCleanupAdapter;
import com.sub9.orderservice.cart.infrastructure.persistence.CartJpaRepository;
import com.sub9.orderservice.order.application.port.output.*;
import com.sub9.orderservice.order.domain.model.*;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import com.sub9.orderservice.payment.application.service.MockPaymentService;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import com.sub9.orderservice.order.domain.repository.CartCleanupTaskRepository;
import com.sub9.orderservice.order.infrastructure.persistence.CartCleanupTaskJpaRepository;
import com.sub9.orderservice.order.infrastructure.persistence.CartCleanupTaskRepositoryAdapter;
import com.sub9.orderservice.payment.infrastructure.persistence.PaymentRepositoryAdapter;
import com.sub9.orderservice.payment.domain.model.Payment;
import jakarta.persistence.EntityManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.sub9.orderservice.order.infrastructure.scheduling.CartCleanupScheduler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "order.expiration.enabled=false", "order.cart-cleanup.enabled=false",
        "management.tracing.export.enabled=false"
})
@MockitoBean(types = {CartQueryService.class, CouponApplicationPort.class, CouponUsagePort.class,
        StockPort.class, PaymentCancellationPort.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("결제 후 장바구니 정리 PostgreSQL 통합 검증")
class CartCleanupIntegrationTest {
    static final Instant NOW = Instant.parse("2026-09-10T00:00:30Z");
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired CartCleanupTaskRepository tasks;
    @Autowired CartCleanupTaskJpaRepository taskJpa;
    @Autowired CartJpaRepository carts;
    @Autowired CartCleanupTransactionService worker;
    @Autowired JsonMapper mapper;
    @Autowired MockPaymentService payments;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired OrderExpirationTransactionService expiration;
    @MockitoSpyBean PaymentRepositoryAdapter paymentRepository;
    @MockitoSpyBean CartCleanupTaskRepositoryAdapter taskRepository;
    @MockitoSpyBean CartCleanupAdapter cleanup;
    @MockitoBean Clock clock;
    @MockitoBean KafkaTemplate<String, String> kafka;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void clearDatabase() {
        taskJpa.deleteAll();
        carts.deleteAll();
        jdbc.update("delete from public.p_payments");
        jdbc.update("delete from p_order_items");
        jdbc.update("delete from p_orders");
    }

    @Test
    @DisplayName("선택한 본인 항목만 삭제하고 중복 실행에도 새로 담은 항목을 유지한다")
    void when_cleanup_runs_only_selected_owned_items_are_deleted() {
        Cart selected = cart(UUID.randomUUID());
        Cart unselected = cart(selected.getUserId());
        Cart other = cart(UUID.randomUUID());
        CartCleanupTask task = enqueue(selected, other.getId());

        scheduler(NOW).cleanup();

        assertThat(carts.findById(selected.getId())).isEmpty();
        assertThat(carts.findById(unselected.getId())).isPresent();
        assertThat(carts.findById(other.getId())).isPresent();
        assertThat(taskJpa.findById(task.getId())).isEmpty();
        Cart added = carts.save(Cart.create(UUID.randomUUID(), selected.getUserId(), selected.getSkuId(), 3));
        worker.process(task.getId(), NOW);
        // 동일 요청이 다시 전달되어도 원본 ID만 삭제한다.
        tasks.save(new CartCleanupTask(UUID.randomUUID(), task.getOrderId(), task.getPayload(), NOW));
        scheduler(NOW).cleanup();
        assertThat(carts.findById(added.getId())).isPresent();
    }

    @Test
    @DisplayName("삭제 후 예외가 나면 삭제와 작업 완료를 롤백하고 새 실행기에서 재처리한다")
    void when_deletion_fails_retry_recovers_rolled_back_task() {
        Cart selected = cart(UUID.randomUUID());
        Order order = order(selected);
        payments.process(order.getCustomerId(), order.getOrderNumber(), PaymentStatus.SUCCESS);
        CartCleanupTask task = tasks.findDue(NOW, 100).getFirst();
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("삭제 이후 장애");
        }).when(cleanup).cleanup(any());

        scheduler(NOW).cleanup();

        assertThat(carts.findById(selected.getId())).isPresent();
        assertThat(status(order)).isEqualTo("PAID");
        assertThat(paymentRepository.findByOrderId(order.getId())).isPresent();
        assertThat(taskJpa.findById(task.getId()).orElseThrow().getNextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
        reset(cleanup);
        scheduler(NOW.plusSeconds(59)).cleanup();
        assertThat(carts.findById(selected.getId())).isPresent();
        scheduler(NOW.plusSeconds(60)).cleanup();
        assertThat(carts.findById(selected.getId())).isEmpty();
        assertThat(taskJpa.findById(task.getId())).isEmpty();
    }

    @Test
    @DisplayName("재시도 시각 저장도 실패하면 작업을 남기고 다음 작업을 계속 처리한다")
    void when_retry_recording_fails_task_is_preserved() {
        Cart first = cart(UUID.randomUUID());
        CartCleanupTask failed = enqueue(first);
        Cart second = cart(UUID.randomUUID());
        CartCleanupTask successful = enqueue(second);
        CartCleanupTransactionService failing = mock(CartCleanupTransactionService.class);
        doThrow(new IllegalStateException("삭제 실패")).when(failing).process(failed.getId(), NOW);
        doThrow(new IllegalStateException("재시도 기록 실패")).when(failing).postpone(eq(failed.getId()), any());
        doAnswer(call -> { worker.process(successful.getId(), NOW); return null; })
                .when(failing).process(successful.getId(), NOW);

        new CartCleanupScheduler(tasks, failing, Clock.fixed(NOW, ZoneOffset.UTC)).cleanup();

        assertThat(taskJpa.findById(failed.getId())).isPresent();
        assertThat(carts.findById(first.getId())).isPresent();
        assertThat(taskJpa.findById(successful.getId())).isEmpty();
        scheduler(NOW).cleanup();
        assertThat(taskJpa.findById(failed.getId())).isEmpty();
    }

    @Test
    @DisplayName("결제 성공 후 작업을 저장하고 별도 실행에서 장바구니를 삭제한다")
    void when_payment_succeeds_cleanup_deletes_cart_items() {
        Cart selected = cart(UUID.randomUUID());
        Order order = order(selected);
        payments.process(order.getCustomerId(), order.getOrderNumber(), PaymentStatus.SUCCESS);
        assertThat(taskJpa.count()).isEqualTo(1);
        assertThat(carts.findById(selected.getId())).isPresent();
        scheduler(NOW).cleanup();
        assertThat(carts.findById(selected.getId())).isEmpty();
        assertThat(taskJpa.count()).isZero();
        payments.process(order.getCustomerId(), order.getOrderNumber(), PaymentStatus.SUCCESS);
        assertThat(taskJpa.count()).isZero();
    }

    @Test
    @DisplayName("결제가 커밋되기 전에는 별도 실행기가 작업을 볼 수 없다")
    void when_payment_is_being_saved_cleanup_does_not_delete_before_commit() {
        Cart selected = cart(UUID.randomUUID());
        Order order = order(selected);
        doAnswer(call -> {
            Object saved = call.callRealMethod();
            entityManager.flush();
            assertThat(taskJpa.count()).isEqualTo(1);
            CompletableFuture.runAsync(() -> scheduler(NOW).cleanup()).get(5, TimeUnit.SECONDS);
            assertThat(carts.findById(selected.getId())).isPresent();
            verifyNoInteractions(cleanup);
            return saved;
        }).when(paymentRepository).save(any(Payment.class));
        payments.process(order.getCustomerId(), order.getOrderNumber(), PaymentStatus.SUCCESS);
        scheduler(NOW).cleanup();
        assertThat(carts.findById(selected.getId())).isEmpty();
    }

    @Test
    @DisplayName("결제 저장 후 롤백되면 정리 작업과 결제 기록도 남지 않는다")
    void when_payment_rolls_back_cleanup_task_and_deletion_are_absent() {
        Cart selected = cart(UUID.randomUUID());
        Order order = order(selected);
        doAnswer(call -> {
            call.callRealMethod();
            entityManager.flush();
            assertThat(taskJpa.count()).isEqualTo(1);
            throw new IllegalStateException("결제 저장 이후 장애");
        }).when(paymentRepository).save(any(Payment.class));
        assertThatThrownBy(() -> payments.process(order.getCustomerId(), order.getOrderNumber(), PaymentStatus.SUCCESS))
                .hasRootCauseMessage("결제 저장 이후 장애");
        assertThat(taskJpa.count()).isZero();
        assertThat(paymentRepository.findByOrderId(order.getId())).isEmpty();
        assertThat(status(order)).isEqualTo("PENDING_PAYMENT");
        scheduler(NOW).cleanup();
        assertThat(carts.findById(selected.getId())).isPresent();
    }

    @Test
    @DisplayName("작업 저장이 실패하면 결제 성공도 함께 롤백한다")
    void when_task_storage_fails_payment_success_is_rolled_back() {
        Cart selected = cart(UUID.randomUUID());
        Order order = order(selected);
        doAnswer(call -> {
            call.callRealMethod();
            entityManager.flush();
            throw new IllegalStateException("작업 저장 장애");
        }).when(taskRepository).save(any(CartCleanupTask.class));
        assertThatThrownBy(() -> payments.process(order.getCustomerId(), order.getOrderNumber(), PaymentStatus.SUCCESS))
                .hasRootCauseMessage("작업 저장 장애");
        assertThat(taskJpa.count()).isZero();
        assertThat(paymentRepository.findByOrderId(order.getId())).isEmpty();
        assertThat(status(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(carts.findById(selected.getId())).isPresent();
    }

    @Test
    @DisplayName("결제 실패와 주문 만료는 정리 작업을 만들지 않는다")
    void when_payment_fails_or_order_expires_cart_items_are_preserved() {
        Cart failedCart = cart(UUID.randomUUID());
        Order failed = order(failedCart);
        payments.process(failed.getCustomerId(), failed.getOrderNumber(), PaymentStatus.FAILED);
        Cart expiredCart = cart(UUID.randomUUID());
        Order expired = order(expiredCart);
        expiration.expire(expired.getId(), expired.getExpiresAt());
        assertThat(status(failed)).isEqualTo("FAILED");
        assertThat(status(expired)).isEqualTo("EXPIRED");
        assertThat(taskJpa.count()).isZero();
        scheduler(NOW).cleanup();
        assertThat(carts.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("원본 ID가 없는 주문은 제외하고 일부만 있으면 해당 항목만 정리한다")
    void when_source_ids_are_missing_cleanup_deletes_only_known_items() {
        Cart legacyCart = cart(UUID.randomUUID());
        Order legacy = order(legacyCart, java.util.Arrays.asList((UUID) null));
        payments.process(legacy.getCustomerId(), legacy.getOrderNumber(), PaymentStatus.SUCCESS);
        assertThat(taskJpa.count()).isZero();
        Cart selected = cart(UUID.randomUUID());
        Order partial = order(selected, java.util.Arrays.asList(null, selected.getId()));
        payments.process(partial.getCustomerId(), partial.getOrderNumber(), PaymentStatus.SUCCESS);
        CartCleanupCommand command = mapper.readValue(tasks.findDue(NOW, 100).getFirst().getPayload(), CartCleanupCommand.class);
        assertThat(command.cartItemIds()).containsExactly(selected.getId());
        scheduler(NOW).cleanup();
        assertThat(carts.findById(selected.getId())).isEmpty();
        assertThat(carts.findById(legacyCart.getId())).isPresent();
    }

    @Test
    @DisplayName("주문 후 수량 변경은 원본을 삭제하고 삭제 후 다시 담은 항목은 유지한다")
    void when_cart_changes_after_order_cleanup_uses_original_item_ids() {
        Cart changed = cart(UUID.randomUUID());
        Order first = order(changed);
        changed.changeQuantity(5);
        carts.save(changed);
        Cart removed = cart(UUID.randomUUID());
        Order second = order(removed);
        carts.deleteById(removed.getId());
        Cart added = carts.save(Cart.create(UUID.randomUUID(), removed.getUserId(), removed.getSkuId(), 3));
        payments.process(first.getCustomerId(), first.getOrderNumber(), PaymentStatus.SUCCESS);
        payments.process(second.getCustomerId(), second.getOrderNumber(), PaymentStatus.SUCCESS);
        scheduler(NOW).cleanup();
        assertThat(carts.findById(changed.getId())).isEmpty();
        assertThat(carts.findById(added.getId())).isPresent();
        assertThat(taskJpa.count()).isZero();
    }

    @Test
    @DisplayName("동시에 같은 작업을 실행하면 잠금을 얻은 실행기만 삭제한다")
    void when_same_task_runs_concurrently_items_are_deleted_once() throws Exception {
        Cart selected = cart(UUID.randomUUID());
        CartCleanupTask task = enqueue(selected);
        CountDownLatch deleting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            call.callRealMethod();
            deleting.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(cleanup).cleanup(any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> worker.process(task.getId(), NOW));
            try {
                assertThat(deleting.await(5, TimeUnit.SECONDS)).isTrue();
                executor.submit(() -> worker.process(task.getId(), NOW)).get(5, TimeUnit.SECONDS);
                assertThat(taskJpa.findById(task.getId())).isPresent();
            } finally {
                release.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
        }
        verify(cleanup, times(1)).cleanup(any());
        assertThat(taskJpa.findById(task.getId())).isEmpty();
        assertThat(carts.findById(selected.getId())).isEmpty();
    }

    @Test
    @DisplayName("작업 완료 저장이 실패하면 장바구니 삭제도 롤백한다")
    void when_completion_storage_fails_retry_recovers_deletion() {
        Cart selected = cart(UUID.randomUUID());
        CartCleanupTask task = enqueue(selected);
        doAnswer(call -> {
            call.callRealMethod();
            entityManager.flush();
            throw new IllegalStateException("완료 저장 장애");
        }).when(taskRepository).delete(any());
        scheduler(NOW).cleanup();
        assertThat(carts.findById(selected.getId())).isPresent();
        assertThat(taskJpa.findById(task.getId())).isPresent();
        reset(taskRepository);
        scheduler(NOW.plusSeconds(60)).cleanup();
        assertThat(carts.findById(selected.getId())).isEmpty();
        assertThat(taskJpa.findById(task.getId())).isEmpty();
    }

    private String status(Order order) {
        return jdbc.queryForObject("select status from p_orders where id = ?", String.class, order.getId());
    }

    private Order order(Cart cart) {
        return order(cart, List.of(cart.getId()));
    }

    private Order order(Cart cart, List<UUID> sourceIds) {
        Order order = Order.create(new com.sub9.common.identifier.UuidV7Generator().generate(), cart.getUserId(),
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울 강남구", "101호"),
                sourceIds.stream().map(sourceId -> OrderItem.create(new com.sub9.common.identifier.UuidV7Generator().generate(),
                        sourceId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                        ProductSnapshot.of("키링", "기본", Money.won(1000), 2), Money.won(0))).toList(),
                NOW.minusSeconds(30));
        return orders.save(order);
    }

    private CartCleanupScheduler scheduler(Instant now) {
        return new CartCleanupScheduler(tasks, worker, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Cart cart(UUID userId) {
        return carts.save(Cart.create(UUID.randomUUID(), userId, UUID.randomUUID(), 2));
    }

    private CartCleanupTask enqueue(Cart cart, UUID... otherIds) {
        var ids = new java.util.ArrayList<>(List.of(cart.getId()));
        ids.addAll(List.of(otherIds));
        CartCleanupCommand command = new CartCleanupCommand(UUID.randomUUID(), cart.getUserId(), ids);
        return tasks.save(new CartCleanupTask(UUID.randomUUID(), command.orderId(),
                mapper.writeValueAsString(command), NOW));
    }
}
