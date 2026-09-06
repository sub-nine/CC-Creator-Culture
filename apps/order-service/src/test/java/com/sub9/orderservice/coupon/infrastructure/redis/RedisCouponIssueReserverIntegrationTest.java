package com.sub9.orderservice.coupon.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.application.dto.CouponIssueTarget;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("쿠폰 Redis 원자 선점 통합")
class RedisCouponIssueReserverIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    private static final UUID COUPON_ID = UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("01990a00-0000-7000-8000-000000000002");
    private static final UUID RESERVATION_ID = UUID.fromString("01990a00-0000-7000-8000-000000000003");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
                    DockerImageName.parse("redis:7.4.11-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisCouponIssueReserver reserver;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        reserver = new RedisCouponIssueReserver(
                redisTemplate, new CouponIssueReserveScript(), new CouponIssueReleaseScript(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterAll
    static void tearDownRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("선점 성공 시 잔여 수량을 감소시키고 reservationId와 TTL을 저장한다")
    void reserves_quantity_and_stores_owner_with_ttl() {
        redisTemplate.opsForValue().set(CouponRedisKey.remaining(COUPON_ID), "2");

        reserver.reserve(target(2), reservation(USER_ID, RESERVATION_ID));

        String issuedKey = CouponRedisKey.issued(COUPON_ID, USER_ID);
        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(COUPON_ID))).isEqualTo("1");
        assertThat(redisTemplate.opsForValue().get(issuedKey)).isEqualTo(RESERVATION_ID.toString());
        assertThat(redisTemplate.getExpire(issuedKey)).isPositive().isLessThanOrEqualTo(600);
    }

    @Test
    @DisplayName("동일 사용자의 두 번째 선점을 중복으로 거부하고 수량을 유지한다")
    void rejects_duplicate_without_decreasing_quantity_again() {
        redisTemplate.opsForValue().set(CouponRedisKey.remaining(COUPON_ID), "2");
        reserver.reserve(target(2), reservation(USER_ID, RESERVATION_ID));

        assertCouponError(
                () -> reserver.reserve(target(2), reservation(USER_ID, new UuidV7Generator().generate())),
                CouponErrorCode.ALREADY_ISSUED);
        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(COUPON_ID))).isEqualTo("1");
    }

    @Test
    @DisplayName("잔여 수량이 0이면 품절로 거부하고 음수로 감소시키지 않는다")
    void rejects_sold_out_without_negative_quantity() {
        redisTemplate.opsForValue().set(CouponRedisKey.remaining(COUPON_ID), "0");

        assertCouponError(
                () -> reserver.reserve(target(0), reservation(USER_ID, RESERVATION_ID)),
                CouponErrorCode.SOLD_OUT);
        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(COUPON_ID))).isEqualTo("0");
    }

    @Test
    @DisplayName("잔여 수량 키가 없으면 DB 잔여 수량으로 초기화한 뒤 선점한다")
    void lazily_initializes_missing_quantity_and_reserves() {
        reserver.reserve(target(2), reservation(USER_ID, RESERVATION_ID));

        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(COUPON_ID))).isEqualTo("1");
    }

    @Test
    @DisplayName("동시 요청에서도 성공 수가 잔여 수량을 넘지 않고 수량이 음수가 되지 않는다")
    void concurrent_reservations_do_not_exceed_quantity() throws Exception {
        int quantity = 5;
        redisTemplate.opsForValue().set(CouponRedisKey.remaining(COUPON_ID), Integer.toString(quantity));
        UuidV7Generator generator = new UuidV7Generator();
        List<Callable<Boolean>> requests = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            UUID userId = generator.generate();
            UUID reservationId = generator.generate();
            requests.add(() -> {
                try {
                    reserver.reserve(target(quantity), reservation(userId, reservationId));
                    return true;
                } catch (BusinessException exception) {
                    return false;
                }
            });
        }

        try (var executor = Executors.newFixedThreadPool(10)) {
            long successCount = executor.invokeAll(requests).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .count();

            assertThat(successCount).isEqualTo(quantity);
            assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(COUPON_ID))).isEqualTo("0");
        }
    }

    @Test
    @DisplayName("선점 소유자가 보상하면 사용자 선점 키를 삭제하고 잔여 수량을 복구한다")
    void owner_releases_reservation_and_restores_quantity() {
        redisTemplate.opsForValue().set(CouponRedisKey.remaining(COUPON_ID), "2");
        CouponReservation reservation = reservation(USER_ID, RESERVATION_ID);
        reserver.reserve(target(2), reservation);

        reserver.rollback(reservation);

        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(COUPON_ID))).isEqualTo("2");
        assertThat(redisTemplate.hasKey(CouponRedisKey.issued(COUPON_ID, USER_ID))).isFalse();
    }

    @Test
    @DisplayName("같은 보상을 반복해도 잔여 수량은 한 번만 복구한다")
    void repeated_release_restores_quantity_only_once() {
        redisTemplate.opsForValue().set(CouponRedisKey.remaining(COUPON_ID), "2");
        CouponReservation reservation = reservation(USER_ID, RESERVATION_ID);
        reserver.reserve(target(2), reservation);

        reserver.rollback(reservation);
        reserver.rollback(reservation);

        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(COUPON_ID))).isEqualTo("2");
    }

    @Test
    @DisplayName("다른 reservationId는 기존 선점과 잔여 수량을 변경하지 않는다")
    void non_owner_cannot_release_reservation() {
        redisTemplate.opsForValue().set(CouponRedisKey.remaining(COUPON_ID), "2");
        reserver.reserve(target(2), reservation(USER_ID, RESERVATION_ID));

        reserver.rollback(reservation(USER_ID, new UuidV7Generator().generate()));

        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.remaining(COUPON_ID))).isEqualTo("1");
        assertThat(redisTemplate.opsForValue().get(CouponRedisKey.issued(COUPON_ID, USER_ID)))
                .isEqualTo(RESERVATION_ID.toString());
    }

    @Test
    @DisplayName("잔여 수량 키가 소실되면 임의 수량을 만들지 않고 소유한 선점 키만 정리한다")
    void missing_quantity_key_does_not_create_inaccurate_quantity() {
        redisTemplate.opsForValue().set(CouponRedisKey.remaining(COUPON_ID), "2");
        CouponReservation reservation = reservation(USER_ID, RESERVATION_ID);
        reserver.reserve(target(2), reservation);
        redisTemplate.delete(CouponRedisKey.remaining(COUPON_ID));

        reserver.rollback(reservation);

        assertThat(redisTemplate.hasKey(CouponRedisKey.remaining(COUPON_ID))).isFalse();
        assertThat(redisTemplate.hasKey(CouponRedisKey.issued(COUPON_ID, USER_ID))).isFalse();
    }

    private CouponIssueTarget target(int remainingQuantity) {
        return new CouponIssueTarget(COUPON_ID, NOW.plusSeconds(600), remainingQuantity);
    }

    private CouponReservation reservation(UUID userId, UUID reservationId) {
        return new CouponReservation(COUPON_ID, userId, reservationId);
    }

    private void assertCouponError(Runnable action, CouponErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
