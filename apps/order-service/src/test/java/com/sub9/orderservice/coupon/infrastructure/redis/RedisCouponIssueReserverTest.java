package com.sub9.orderservice.coupon.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.coupon.application.dto.CouponIssueTarget;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 Redis 원자 선점")
class RedisCouponIssueReserverTest {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    private static final UUID COUPON_ID = UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("01990a00-0000-7000-8000-000000000002");
    private static final UUID RESERVATION_ID = UUID.fromString("01990a00-0000-7000-8000-000000000003");

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private CouponIssueReserveScript reserveScript;
    @Mock private RedisScript<Long> script;
    @Mock private ValueOperations<String, String> valueOperations;
    private RedisCouponIssueReserver reserver;

    @BeforeEach
    void setUp() {
        when(reserveScript.value()).thenReturn(script);
        reserver = new RedisCouponIssueReserver(
                redisTemplate, reserveScript, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Lua 선점 성공 결과를 정상 처리하고 쿠폰 만료까지의 TTL을 전달한다")
    void handles_success_and_passes_expiration_ttl() {
        whenExecuteReserve().thenReturn(1L);

        assertThatCode(() -> reserver.reserve(target(), reservation())).doesNotThrowAnyException();

        verify(redisTemplate).execute(
                script,
                List.of(CouponRedisKey.remaining(COUPON_ID), CouponRedisKey.issued(COUPON_ID, USER_ID)),
                "600", RESERVATION_ID.toString());
    }

    @Test
    @DisplayName("Lua 중복 결과를 이미 발급된 쿠폰 오류로 변환한다")
    void converts_duplicate_result() {
        whenExecuteReserve().thenReturn(-1L);

        assertCouponError(CouponErrorCode.ALREADY_ISSUED);
    }

    @Test
    @DisplayName("Lua 품절 결과를 쿠폰 품절 오류로 변환한다")
    void converts_sold_out_result() {
        whenExecuteReserve().thenReturn(-2L);

        assertCouponError(CouponErrorCode.SOLD_OUT);
    }

    @Test
    @DisplayName("잔여 수량 키가 없으면 DB 잔여 수량으로 한 번 초기화하고 재시도한다")
    void initializes_missing_quantity_once_and_retries() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        whenExecuteReserve().thenReturn(-3L, 1L);

        reserver.reserve(target(), reservation());

        verify(valueOperations).setIfAbsent(CouponRedisKey.remaining(COUPON_ID), "10");
        verify(redisTemplate, org.mockito.Mockito.times(2)).execute(
                script,
                List.of(CouponRedisKey.remaining(COUPON_ID), CouponRedisKey.issued(COUPON_ID, USER_ID)),
                "600", RESERVATION_ID.toString());
    }

    @Test
    @DisplayName("Redis 호출 실패를 쿠폰 Redis 저장 오류로 변환한다")
    void converts_redis_failure() {
        whenExecuteReserve().thenThrow(new QueryTimeoutException("Redis timeout"));

        assertThatThrownBy(() -> reserver.reserve(target(), reservation()))
                .isInstanceOf(CouponRedisStorageException.class);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("알 수 없는 Lua 결과는 내부 상태 오류로 처리한다")
    void rejects_unknown_result() {
        whenExecuteReserve().thenReturn(99L);

        assertThatThrownBy(() -> reserver.reserve(target(), reservation()))
                .isInstanceOf(IllegalStateException.class);
    }

    private org.mockito.stubbing.OngoingStubbing<Long> whenExecuteReserve() {
        return when(redisTemplate.execute(
                script,
                List.of(CouponRedisKey.remaining(COUPON_ID), CouponRedisKey.issued(COUPON_ID, USER_ID)),
                "600", RESERVATION_ID.toString()));
    }

    private CouponIssueTarget target() {
        return new CouponIssueTarget(COUPON_ID, NOW.plusSeconds(600), 10);
    }

    private CouponReservation reservation() {
        return new CouponReservation(COUPON_ID, USER_ID, RESERVATION_ID);
    }

    private void assertCouponError(CouponErrorCode errorCode) {
        assertThatThrownBy(() -> reserver.reserve(target(), reservation()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
