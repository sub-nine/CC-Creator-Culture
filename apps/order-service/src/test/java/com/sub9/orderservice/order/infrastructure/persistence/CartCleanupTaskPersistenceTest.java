package com.sub9.orderservice.order.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import com.sub9.orderservice.order.domain.model.CartCleanupTask;
import com.sub9.orderservice.order.domain.repository.CartCleanupTaskRepository;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CartCleanupTaskRepositoryAdapter.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("장바구니 정리 작업 PostgreSQL 저장")
class CartCleanupTaskPersistenceTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    @Autowired CartCleanupTaskRepository repository;
    @Autowired CartCleanupTaskJpaRepository jpa;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @DisplayName("같은 주문의 작업을 다시 저장하면 유일성 제약으로 거부한다")
    void 동일_주문일_때_다시_저장하면_중복을_거부한다() {
        CartCleanupTask first = task(NOW);
        repository.save(first);
        jpa.flush();
        assertThatThrownBy(() -> {
            repository.save(new CartCleanupTask(UUID.randomUUID(), first.getOrderId(), "{}", NOW));
            jpa.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("재시도를 미루면 지정한 시각에만 다시 선택한다")
    void 실패한_작업일_때_재시도를_미루면_기한까지_제외한다() {
        CartCleanupTask task = repository.save(task(NOW));
        repository.save(task(NOW.plusSeconds(120)));
        assertThat(repository.findDue(NOW, 1)).extracting(CartCleanupTask::getId).containsExactly(task.getId());
        repository.postpone(task.getId(), NOW.plusSeconds(60));
        assertThat(repository.findDue(NOW.plusSeconds(59), 100)).isEmpty();
        assertThat(repository.findDue(NOW.plusSeconds(60), 100)).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("다른 트랜잭션이 잠근 작업을 건너뛰고 롤백 후 다시 잠근다")
    void 작업이_잠겼을_때_다시_조회하면_건너뛰고_롤백후_복구한다() {
        TransactionTemplate tx = new TransactionTemplate(manager);
        CartCleanupTask task = tx.execute(status -> repository.save(task(NOW)));
        try (var executor = Executors.newSingleThreadExecutor()) {
            tx.executeWithoutResult(status -> {
                assertThat(repository.findDueForUpdate(task.getId(), NOW)).isPresent();
                try {
                    assertThat(executor.submit(() -> tx.execute(other ->
                            repository.findDueForUpdate(task.getId(), NOW).isEmpty())).get(5, TimeUnit.SECONDS))
                            .isTrue();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
                status.setRollbackOnly();
            });
            tx.executeWithoutResult(status -> {
                CartCleanupTask restored = repository.findDueForUpdate(task.getId(), NOW).orElseThrow();
                assertThat(restored.getPayload()).isEqualTo("{}");
                repository.delete(restored);
            });
            assertThat(jpa.findById(task.getId())).isEmpty();
            tx.executeWithoutResult(status -> repository.postpone(task.getId(), NOW.plusSeconds(60)));
            assertThat(jpa.findById(task.getId())).isEmpty();
        } finally {
            jpa.deleteAll();
        }
    }

    @Test
    @DisplayName("배포 SQL로 만든 테이블에서 작업 저장과 잠금 조회가 동작한다")
    void 배포_SQL일_때_테이블을_생성하면_작업을_처리한다() throws Exception {
        jdbc.execute("create schema cart_cleanup_ddl");
        jdbc.execute("set local search_path to cart_cleanup_ddl, public");
        String sql = Files.readString(Path.of("../../deploy/postgres/create-order-cart-cleanup-tasks.sql"));
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement);
        }
        CartCleanupTask task = repository.save(task(NOW));
        jpa.flush();
        assertThat(repository.findDueForUpdate(task.getId(), NOW)).isPresent();
        assertThat(jdbc.queryForObject("""
                select data_type from information_schema.columns
                 where table_schema = 'cart_cleanup_ddl' and table_name = 'p_order_cart_cleanup_tasks'
                   and column_name = 'created_at'
                """, String.class)).isEqualTo("timestamp without time zone");
    }

    private static CartCleanupTask task(Instant createdAt) {
        return new CartCleanupTask(UUID.randomUUID(), UUID.randomUUID(), "{}", createdAt);
    }
}
