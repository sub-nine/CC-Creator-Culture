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
    void 선택한_항목일_때_작업을_실행하면_본인_원본만_삭제한다() {
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
    void 삭제중_실패일_때_다시_실행하면_롤백한_작업을_복구한다() {
        Cart selected = cart(UUID.randomUUID());
        CartCleanupTask task = enqueue(selected);
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("삭제 이후 장애");
        }).when(cleanup).cleanup(any());

        scheduler(NOW).cleanup();

        assertThat(carts.findById(selected.getId())).isPresent();
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
    void 재시도_기록도_실패일_때_실행하면_작업을_보존한다() {
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
    void 결제_성공일_때_작업을_실행하면_장바구니를_삭제한다() {
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

    private Order order(Cart cart) {
        Order order = Order.create(new com.sub9.common.identifier.UuidV7Generator().generate(), cart.getUserId(),
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울 강남구", "101호"),
                List.of(OrderItem.create(new com.sub9.common.identifier.UuidV7Generator().generate(), cart.getId(), UUID.randomUUID(), UUID.randomUUID(),
                        cart.getSkuId(), null, ProductSnapshot.of("키링", "기본", Money.won(1000), 2), Money.won(0))),
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
