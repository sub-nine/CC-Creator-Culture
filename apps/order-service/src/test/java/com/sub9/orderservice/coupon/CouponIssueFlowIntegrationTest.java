package com.sub9.orderservice.coupon;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.ErrorCode;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;
import com.sub9.orderservice.coupon.application.service.CouponIssueService;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.infrastructure.redis.CouponRedisKey;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
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

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
@MockitoBean(types = {
        CartQueryService.class, CouponApplicationPort.class, CouponUsagePort.class, StockPort.class,
        PaymentCancellationPort.class
})
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
    @DisplayName("Redis 선점 후 사용자 쿠폰을 DB에 커밋한다")
    void issues_coupon_through_redis_and_database() {
        when(clock.instant()).thenReturn(ISSUE_TIME);
        Coupon coupon = saveCoupon(2);
        UUID userId = generator.generate();

        IssueDispatchResult result = couponIssueService.issue(coupon.getId(), userId);

        assertThat(result).isInstanceOf(IssueDispatchResult.Completed.class);
        assertThat(couponRepository.findActiveById(coupon.getId()).orElseThrow().getIssuedQuantity())
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_user_coupons where coupon_id = ? and user_id = ?",
                Integer.class, coupon.getId(), userId)).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(coupon.getId())))
                .isEqualTo("1");
    }

    @Test
    @DisplayName("Redis 선점 후 DB 발급 실패가 확정되면 선점과 수량을 복구한다")
    void releases_redis_reservation_when_database_issue_rolls_back() {
        when(clock.instant()).thenReturn(ISSUE_TIME);
        Coupon coupon = saveCoupon(2);
        UUID userId = generator.generate();

        couponIssueService.issue(coupon.getId(), userId);
        redisTemplate.delete(CouponRedisKey.issued(coupon.getId(), userId));

        assertThatThrownBy(() -> couponIssueService.issue(coupon.getId(), userId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponErrorCode.ALREADY_ISSUED));

        assertThat(couponRepository.findActiveById(coupon.getId()).orElseThrow().getIssuedQuantity())
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_user_coupons where coupon_id = ?",
                Integer.class, coupon.getId())).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(coupon.getId())))
                .isEqualTo("1");
        assertThat(redisTemplate.hasKey(CouponRedisKey.issued(coupon.getId(), userId))).isFalse();
    }

    @Test
    @DisplayName("한정 수량보다 많은 사용자가 동시에 발급해도 성공 수가 총수량을 넘지 않는다")
    void when_users_issue_coupon_concurrently_quantity_is_not_exceeded() throws Exception {
        // 선착순 쿠폰 10개에 서로 다른 사용자 100명이 동시에 요청하는 상황을 재현한다.
        int totalQuantity = 10;
        int requestCount = 100;
        when(clock.instant()).thenReturn(ISSUE_TIME);
        Coupon coupon = saveCoupon(totalQuantity);

        // Redis 지연 초기화가 아닌 정상적인 Redis → DB 발급 경쟁을 검증한다.
        redisTemplate.opsForValue().set(
                CouponRedisKey.remaining(coupon.getId()), Integer.toString(totalQuantity));

        List<UUID> userIds = new ArrayList<>(requestCount);
        for (int index = 0; index < requestCount; index++) {
            userIds.add(generator.generate());
        }

        // 모든 요청을 준비한 뒤 같은 시작 신호를 보내 실제 동시 경쟁을 만든다.
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        List<IssueAttemptResult> attempts = new ArrayList<>(requestCount);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = userIds.stream()
                    .map(userId -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("쿠폰 동시 발급 시작 신호를 기다리는 중 시간 초과했습니다.");
                        }
                        try {
                            couponIssueService.issue(coupon.getId(), userId);
                            return IssueAttemptResult.succeeded(userId);
                        } catch (BusinessException exception) {
                            return IssueAttemptResult.failed(userId, exception.getErrorCode());
                        }
                    }))
                    .toList();

            boolean allRequestsReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allRequestsReady).isTrue();

            for (var future : futures) {
                attempts.add(future.get(30, TimeUnit.SECONDS));
            }
        }

        List<IssueAttemptResult> succeeded = attempts.stream().filter(IssueAttemptResult::isSuccessful).toList();
        List<IssueAttemptResult> failed = attempts.stream().filter(attempt -> !attempt.isSuccessful()).toList();
        Set<UUID> succeededUserIds = succeeded.stream().map(IssueAttemptResult::userId).collect(java.util.stream.Collectors.toSet());
        List<UUID> persistedUserIds = jdbcTemplate.queryForList(
                "select user_id from p_user_coupons where coupon_id = ?",
                UUID.class, coupon.getId());

        assertThat(attempts).hasSize(requestCount);
        // 성공 응답, DB 발급 사용자와 Redis 잔여 수량이 같은 결과를 나타내는지 확인한다.
        assertThat(succeeded).hasSize(totalQuantity);
        assertThat(failed).hasSize(requestCount - totalQuantity)
                .allMatch(attempt -> attempt.errorCode() == CouponErrorCode.SOLD_OUT);
        assertThat(couponRepository.findActiveById(coupon.getId()).orElseThrow().getIssuedQuantity())
                .isZero();
        assertThat(persistedUserIds).hasSize(totalQuantity);
        assertThat(Set.copyOf(persistedUserIds)).isEqualTo(succeededUserIds);
        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(coupon.getId())))
                .isEqualTo("0");
    }

    @Test
    @DisplayName("동일 사용자가 동시에 발급을 요청해도 쿠폰과 수량은 한 번만 반영된다")
    void when_same_user_issues_coupon_concurrently_only_one_succeeds() throws Exception {
        // 동일 사용자의 중복 요청 20개가 동시에 들어오는 상황을 재현한다.
        int totalQuantity = 10;
        int requestCount = 20;
        when(clock.instant()).thenReturn(ISSUE_TIME);
        Coupon coupon = saveCoupon(totalQuantity);
        UUID userId = generator.generate();

        // 수량이 충분한 상태를 준비해 실패 원인이 품절이 아닌 중복 발급임을 검증한다.
        redisTemplate.opsForValue().set(
                CouponRedisKey.remaining(coupon.getId()), Integer.toString(totalQuantity));

        // 동일한 사용자 요청을 모두 준비한 뒤 같은 시작 신호로 발급 경쟁을 만든다.
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        List<IssueAttemptResult> attempts = new ArrayList<>(requestCount);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<IssueAttemptResult>> futures = new ArrayList<>(requestCount);
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동일 사용자 동시 발급 시작 신호를 기다리는 중 시간 초과했습니다.");
                    }
                    try {
                        couponIssueService.issue(coupon.getId(), userId);
                        return IssueAttemptResult.succeeded(userId);
                    } catch (BusinessException exception) {
                        return IssueAttemptResult.failed(userId, exception.getErrorCode());
                    }
                }));
            }

            boolean allRequestsReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allRequestsReady).isTrue();

            for (var future : futures) {
                attempts.add(future.get(30, TimeUnit.SECONDS));
            }
        }

        List<IssueAttemptResult> succeeded = attempts.stream().filter(IssueAttemptResult::isSuccessful).toList();
        List<IssueAttemptResult> failed = attempts.stream().filter(attempt -> !attempt.isSuccessful()).toList();
        String issuedKey = CouponRedisKey.issued(coupon.getId(), userId);

        assertThat(attempts).hasSize(requestCount);
        // 한 요청만 성공하고 나머지는 모두 일관된 중복 발급 오류를 받아야 한다.
        assertThat(succeeded).hasSize(1);
        assertThat(failed).hasSize(requestCount - 1)
                .allMatch(attempt -> attempt.errorCode() == CouponErrorCode.ALREADY_ISSUED);
        // 중복 요청이 DB 발급량과 Redis 잔여 수량을 추가로 변경하지 않았는지 확인한다.
        assertThat(couponRepository.findActiveById(coupon.getId()).orElseThrow().getIssuedQuantity())
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_user_coupons where coupon_id = ? and user_id = ?",
                Integer.class, coupon.getId(), userId)).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(coupon.getId())))
                .isEqualTo("9");
        assertThat(redisTemplate.opsForValue().get(issuedKey)).isNotBlank();
    }

    private Coupon saveCoupon(int totalQuantity) {
        return couponRepository.save(Coupon.create(
                generator.generate(), "전체 흐름 쿠폰", 10, totalQuantity,
                ISSUE_TIME.minusSeconds(60), ISSUE_TIME.plusSeconds(60),
                generator.generate(), ISSUE_TIME.minusSeconds(120)));
    }

    // 각 요청의 사용자와 오류 코드를 보존해 성공 사용자와 실패 원인을 함께 검증한다.
    private record IssueAttemptResult(UUID userId, ErrorCode errorCode) {

        private static IssueAttemptResult succeeded(UUID userId) {
            return new IssueAttemptResult(userId, null);
        }

        private static IssueAttemptResult failed(UUID userId, ErrorCode errorCode) {
            return new IssueAttemptResult(userId, errorCode);
        }

        private boolean isSuccessful() {
            return errorCode == null;
        }
    }
}
