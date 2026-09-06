package com.sub9.orderservice.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;
import com.sub9.orderservice.coupon.application.service.CouponIssueService;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.infrastructure.redis.CouponRedisKey;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

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
@DisplayName("쿠폰 Redis 선점과 동기 DB 발급 전체 흐름")
class CouponIssueFlowIntegrationTest {

    private static final Instant ISSUE_TIME = Instant.parse("2026-09-06T00:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("coupon_issue_flow_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
                    DockerImageName.parse("redis:7.4.11-alpine"))
            .withExposedPorts(6379);

    @Autowired private CouponIssueService couponIssueService;
    @Autowired private CouponRepository couponRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private Clock clock;
    private final UuidV7Generator generator = new UuidV7Generator();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @AfterEach
    void cleanStores() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbcTemplate.update("delete from p_user_coupons");
        jdbcTemplate.update("delete from p_coupons");
    }

    @Test
    @DisplayName("Redis 선점 후 발급 수량과 사용자 쿠폰을 DB에 커밋한다")
    void issues_coupon_through_redis_and_database() {
        when(clock.instant()).thenReturn(ISSUE_TIME);
        Coupon coupon = saveCoupon(2);
        UUID userId = generator.generate();

        IssueDispatchResult result = couponIssueService.issue(coupon.getId(), userId);

        assertThat(result).isInstanceOf(IssueDispatchResult.Completed.class);
        assertThat(couponRepository.findActiveById(coupon.getId()).orElseThrow().getIssuedQuantity())
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_user_coupons where coupon_id = ? and user_id = ?",
                Integer.class, coupon.getId(), userId)).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(coupon.getId())))
                .isEqualTo("1");
    }

    @Test
    @DisplayName("Redis 선점 후 DB 발급 실패가 확정되면 선점과 수량을 복구한다")
    void releases_redis_reservation_when_database_issue_rolls_back() {
        when(clock.instant()).thenReturn(
                ISSUE_TIME,
                ISSUE_TIME,
                ISSUE_TIME.plusSeconds(120));
        Coupon coupon = saveCoupon(2);
        UUID userId = generator.generate();

        assertThatThrownBy(() -> couponIssueService.issue(coupon.getId(), userId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponErrorCode.NOT_IN_ISSUE_PERIOD));

        assertThat(couponRepository.findActiveById(coupon.getId()).orElseThrow().getIssuedQuantity())
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_user_coupons where coupon_id = ?",
                Integer.class, coupon.getId())).isZero();
        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(coupon.getId())))
                .isEqualTo("2");
        assertThat(redisTemplate.hasKey(CouponRedisKey.issued(coupon.getId(), userId))).isFalse();
    }

    private Coupon saveCoupon(int totalQuantity) {
        return couponRepository.save(Coupon.create(
                generator.generate(), "전체 흐름 쿠폰", 10, totalQuantity,
                ISSUE_TIME.minusSeconds(60), ISSUE_TIME.plusSeconds(60),
                generator.generate(), ISSUE_TIME.minusSeconds(120)));
    }
}
