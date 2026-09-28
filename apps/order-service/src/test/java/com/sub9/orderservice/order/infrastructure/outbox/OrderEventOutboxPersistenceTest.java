package com.sub9.orderservice.order.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
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

// 배포와 같이 Flyway로 스키마를 만들고 ddl-auto=validate로 엔티티와 대조한다.
@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("주문 이벤트 Outbox PostgreSQL 저장")
class OrderEventOutboxPersistenceTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");
    @Autowired OrderEventOutboxJpaRepository outbox;
    @Autowired PlatformTransactionManager manager;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @DisplayName("다음 시도 시각이 된 기록만 오래된 순서로 점유한다")
    void when_records_are_due_only_due_records_are_claimed_in_order() {
        OrderEventOutbox older = outbox.saveAndFlush(record(NOW.minusSeconds(1)));
        OrderEventOutbox due = outbox.saveAndFlush(record(NOW));
        outbox.saveAndFlush(record(NOW.plusSeconds(1)));

        assertThat(outbox.findDueForUpdate(NOW, 10)).extracting(OrderEventOutbox::getId)
                .containsExactly(older.getId(), due.getId());
        assertThat(outbox.findDueForUpdate(NOW, 1)).extracting(OrderEventOutbox::getId)
                .containsExactly(older.getId());
    }

    @Test
    @DisplayName("점유한 기록의 다음 시도를 미루면 그 시각 전에는 다시 점유하지 않는다")
    void when_claimed_records_are_postponed_they_are_not_claimed_until_due() {
        OrderEventOutbox record = outbox.saveAndFlush(record(NOW));

        outbox.postpone(List.of(record.getId()), NOW.plusSeconds(60));

        assertThat(outbox.findDueForUpdate(NOW.plusSeconds(59), 10)).isEmpty();
        assertThat(outbox.findDueForUpdate(NOW.plusSeconds(60), 10)).extracting(OrderEventOutbox::getId)
                .containsExactly(record.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("다른 트랜잭션이 점유 중인 기록은 기다리지 않고 건너뛴다")
    void when_record_is_locked_by_another_transaction_it_is_skipped() {
        TransactionTemplate tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> outbox.save(record(NOW)));
        try (var executor = Executors.newSingleThreadExecutor()) {
            tx.executeWithoutResult(status -> {
                assertThat(outbox.findDueForUpdate(NOW, 10)).hasSize(1);
                try {
                    assertThat(executor.submit(() -> tx.execute(other -> outbox.findDueForUpdate(NOW, 10)))
                            .get(5, TimeUnit.SECONDS)).isEmpty();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
        } finally {
            outbox.deleteAll();
        }
    }

    private static OrderEventOutbox record(Instant createdAt) {
        return new OrderEventOutbox(UUID.randomUUID(), "order.paid", UUID.randomUUID().toString(), "{}", createdAt);
    }
}
