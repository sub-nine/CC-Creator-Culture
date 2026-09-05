package com.sub9.orderservice.order.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderQueryRepository;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
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
import org.springframework.data.domain.PageRequest;
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
@DisplayName("주문 도메인 PostgreSQL 영속성")
class OrderPersistenceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-03T00:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_service_test")
            .withUsername("test")
            .withPassword("test");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderQueryRepository orderQueryRepository;

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
        jdbcTemplate.update("delete from p_order_items");
        jdbcTemplate.update("delete from p_orders");
    }

    @Test
    @DisplayName("주문 애그리거트를 저장하고 주문번호 상세와 일반 목록으로 조회한다")
    void when_order_is_saved_aggregate_is_restored_by_basic_queries() {
        Order order = order(2);
        saveAndFlush(order);

        Order detail = orderQueryRepository.findDetailByOrderNumber(order.getOrderNumber()).orElseThrow();

        assertThat(detail.getId()).isEqualTo(order.getId());
        assertThat(detail.getItems()).hasSize(2);
        assertThat(detail.getCreatedAt()).isNotNull();
        assertThat(detail.getUpdatedAt()).isNotNull();
        assertThat(detail.getItems().getFirst().getOrderId()).isEqualTo(order.getId());
        assertThat(orderQueryRepository.findAllOrders(PageRequest.of(0, 20)))
                .extracting(Order::getId)
                .containsExactly(order.getId());
    }

    @Test
    @DisplayName("주문 테이블의 키, 제약, 인덱스와 시간 타입을 생성한다")
    void when_schema_is_created_expected_constraints_indexes_and_timestamp_types_exist() {
        Set<String> constraints = Set.copyOf(jdbcTemplate.queryForList("""
                select constraint_name
                  from information_schema.table_constraints
                 where table_schema = 'public'
                   and table_name in ('p_orders', 'p_order_items')
                """, String.class));
        Set<String> indexes = Set.copyOf(jdbcTemplate.queryForList("""
                select indexname
                  from pg_indexes
                 where schemaname = 'public'
                   and tablename in ('p_orders', 'p_order_items')
                """, String.class));

        assertThat(constraints).contains(
                "uk_orders_order_number",
                "uk_order_items_order_sku",
                "fk_order_items_order",
                "ck_orders_status",
                "ck_orders_paid_at",
                "ck_orders_payment_amount",
                "ck_order_items_status",
                "ck_order_items_payment_amount");
        assertThat(indexes).contains(
                "idx_orders_user_created_at",
                "idx_orders_status_expires_at",
                "idx_order_items_order_id",
                "idx_order_items_creator_status_created");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                  from information_schema.columns
                 where table_schema = 'public'
                   and table_name in ('p_orders', 'p_order_items')
                   and column_name in ('created_at', 'updated_at')
                   and data_type = 'timestamp with time zone'
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                  from information_schema.columns
                 where table_schema = 'public'
                   and table_name = 'p_orders'
                   and column_name in ('expires_at', 'paid_at', 'canceled_at')
                   and data_type = 'timestamp without time zone'
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                  from information_schema.key_column_usage
                 where table_schema = 'public'
                   and table_name in ('p_orders', 'p_order_items')
                   and constraint_name like '%_pkey'
                   and column_name = 'id'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                  from information_schema.table_constraints
                 where table_schema = 'public'
                   and table_name = 'p_order_items'
                   and constraint_type = 'FOREIGN KEY'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("중복 SKU, 잘못된 금액과 존재하지 않는 주문 참조를 데이터베이스가 거부한다")
    void when_database_invariants_are_violated_write_is_rejected() {
        Order order = order(1);
        saveAndFlush(order);
        UUID itemId = order.getItems().getFirst().getId();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into p_order_items (
                    id, order_id, creator_id, product_id, sku_id, user_coupon_id,
                    product_name, sku_name, unit_price, quantity,
                    original_amount, discount_amount, payment_amount, status,
                    created_at, updated_at
                )
                select ?, order_id, creator_id, product_id, sku_id, user_coupon_id,
                       product_name, sku_name, unit_price, quantity,
                       original_amount, discount_amount, payment_amount, status,
                       created_at, updated_at
                  from p_order_items
                 where id = ?
                """, uuidGenerator.generate(), itemId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update p_order_items set payment_amount = payment_amount + 1 where id = ?", itemId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update p_order_items set order_id = ? where id = ?", uuidGenerator.generate(), itemId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update p_orders set status = 'PAID' where id = ?", order.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 주문의 두 잠금 조회를 직렬화한다")
    void when_two_transactions_lock_same_order_second_waits_for_first() throws Exception {
        Order order = order(1);
        saveAndFlush(order);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> transaction().executeWithoutResult(status -> {
                orderRepository.findByIdForUpdate(order.getId()).orElseThrow();
                firstLocked.countDown();
                await(releaseFirst);
            }));

            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<OrderStatus> second = executor.submit(() -> transaction().execute(status ->
                    orderRepository.findByIdForUpdate(order.getId()).orElseThrow().getStatus()));

            Thread.sleep(200);
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();

            first.get(5, TimeUnit.SECONDS);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        } finally {
            releaseFirst.countDown();
        }
    }

    private void saveAndFlush(Order order) {
        transaction().executeWithoutResult(status -> {
            orderRepository.save(order);
            entityManager.flush();
        });
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private Order order(int itemCount) {
        List<OrderItem> items = java.util.stream.IntStream.range(0, itemCount)
                .mapToObj(index -> OrderItem.create(
                        uuidGenerator.generate(),
                        uuidGenerator.generate(),
                        uuidGenerator.generate(),
                        uuidGenerator.generate(),
                        null,
                        ProductSnapshot.of("아크릴 스탠드 " + index, "A 타입", Money.won(18_000), 2),
                        Money.won(1_800)))
                .toList();
        return Order.create(
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울시 강남구", "101호"),
                items,
                CREATED_AT);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("잠금 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("잠금 테스트가 중단되었습니다.", exception);
        }
    }
}
