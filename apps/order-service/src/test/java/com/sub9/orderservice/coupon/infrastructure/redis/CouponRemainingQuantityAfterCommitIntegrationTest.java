package com.sub9.orderservice.coupon.infrastructure.redis;

import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.coupon.application.event.CouponCreatedEvent;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "management.tracing.export.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@MockitoBean(types = {
        CartQueryService.class, CouponApplicationPort.class, CouponUsagePort.class, StockPort.class,
        PaymentCancellationPort.class
})
@DisplayName("쿠폰 Redis 잔여 수량 커밋 후 초기화")
class CouponRemainingQuantityAfterCommitIntegrationTest {

    private static final UUID COMMITTED_COUPON_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID ROLLED_BACK_COUPON_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000002");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_service_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
                    DockerImageName.parse("redis:7.4.11-alpine"))
            .withExposedPorts(6379);

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @AfterEach
    void cleanRedis() {
        redisTemplate.delete(CouponRedisKey.remaining(COMMITTED_COUPON_ID));
        redisTemplate.delete(CouponRedisKey.remaining(ROLLED_BACK_COUPON_ID));
    }

    @Test
    @DisplayName("트랜잭션이 커밋되면 Redis 잔여 수량을 초기화한다")
    void initializes_remaining_quantity_after_commit() {
        transaction().executeWithoutResult(status ->
                eventPublisher.publishEvent(new CouponCreatedEvent(COMMITTED_COUPON_ID, 100)));

        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(COMMITTED_COUPON_ID)))
                .isEqualTo("100");
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 Redis 잔여 수량을 초기화하지 않는다")
    void does_not_initialize_remaining_quantity_after_rollback() {
        transaction().executeWithoutResult(status -> {
            eventPublisher.publishEvent(new CouponCreatedEvent(ROLLED_BACK_COUPON_ID, 100));
            status.setRollbackOnly();
        });

        assertThat(redisTemplate.hasKey(CouponRedisKey.remaining(ROLLED_BACK_COUPON_ID))).isFalse();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
