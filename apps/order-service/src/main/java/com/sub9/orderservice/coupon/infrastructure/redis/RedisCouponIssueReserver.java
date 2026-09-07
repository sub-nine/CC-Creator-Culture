package com.sub9.orderservice.coupon.infrastructure.redis;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.coupon.application.dto.CouponIssueTarget;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.application.port.CouponIssueReserver;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisCouponIssueReserver implements CouponIssueReserver {

    private static final long SUCCESS = 1L;
    private static final long DUPLICATE = -1L;
    private static final long SOLD_OUT = -2L;
    private static final long NOT_INITIALIZED = -3L;
    private static final long RELEASED = 1L;
    private static final long ALREADY_RELEASED = 0L;
    private static final long OWNER_MISMATCH = -1L;
    private static final long RELEASED_WITHOUT_QUANTITY = 2L;

    private final StringRedisTemplate redisTemplate;
    private final CouponIssueReserveScript reserveScript;
    private final CouponIssueReleaseScript releaseScript;
    private final Clock clock;

    @Override
    public void reserve(CouponIssueTarget target, CouponReservation reservation) {
        long ttlSeconds = calculateTtlSeconds(target.expiredAt(), clock.instant());
        Long result = executeReserve(target, reservation, ttlSeconds);
        if (result != null && result == NOT_INITIALIZED) {
            initializeIfAbsent(target);
            result = executeReserve(target, reservation, ttlSeconds);
        }
        handleResult(result);
    }

    @Override
    public void rollback(CouponReservation reservation) {
        Long result;
        try {
            result = redisTemplate.execute(
                    releaseScript.value(),
                    List.of(
                            CouponRedisKey.remaining(reservation.couponId()),
                            CouponRedisKey.issued(reservation.couponId(), reservation.userId())),
                    reservation.reservationId().toString());
        } catch (DataAccessException exception) {
            throw new CouponRedisStorageException();
        }

        handleReleaseResult(result, reservation);
    }

    private Long executeReserve(
            CouponIssueTarget target, CouponReservation reservation, long ttlSeconds) {
        try {
            return redisTemplate.execute(
                    reserveScript.value(),
                    List.of(
                            CouponRedisKey.remaining(target.couponId()),
                            CouponRedisKey.issued(target.couponId(), reservation.userId())),
                    Long.toString(ttlSeconds),
                    reservation.reservationId().toString());
        } catch (DataAccessException exception) {
            throw new CouponRedisStorageException();
        }
    }

    private void initializeIfAbsent(CouponIssueTarget target) {
        try {
            redisTemplate.opsForValue().setIfAbsent(
                    CouponRedisKey.remaining(target.couponId()),
                    Integer.toString(target.remainingQuantity()));
        } catch (DataAccessException exception) {
            throw new CouponRedisStorageException();
        }
    }

    private void handleResult(Long result) {
        if (result == null || result == NOT_INITIALIZED) {
            throw new CouponRedisStorageException();
        }
        if (result == SUCCESS) {
            return;
        }
        if (result == DUPLICATE) {
            throw new BusinessException(CouponErrorCode.ALREADY_ISSUED);
        }
        if (result == SOLD_OUT) {
            throw new BusinessException(CouponErrorCode.SOLD_OUT);
        }
        throw new IllegalStateException("알 수 없는 쿠폰 Redis 선점 결과입니다: " + result);
    }

    private void handleReleaseResult(Long result, CouponReservation reservation) {
        if (result == null) {
            throw new CouponRedisStorageException();
        }
        if (result == RELEASED) {
            log.debug("[쿠폰 발급][Redis 보상 완료] couponId={} reservationId={}",
                    reservation.couponId(), reservation.reservationId());
            return;
        }
        if (result == ALREADY_RELEASED) {
            log.debug("[쿠폰 발급][Redis 보상 생략][이미 해제됨] couponId={} reservationId={}",
                    reservation.couponId(), reservation.reservationId());
            return;
        }
        if (result == OWNER_MISMATCH) {
            log.warn("[쿠폰 발급][Redis 보상 생략][소유권 불일치] couponId={} reservationId={}",
                    reservation.couponId(), reservation.reservationId());
            return;
        }
        if (result == RELEASED_WITHOUT_QUANTITY) {
            log.warn("[쿠폰 발급][Redis 보상 완료][잔여 수량 키 소실] couponId={} reservationId={}",
                    reservation.couponId(), reservation.reservationId());
            return;
        }
        throw new IllegalStateException("알 수 없는 쿠폰 Redis 보상 결과입니다: " + result);
    }

    private long calculateTtlSeconds(Instant expiredAt, Instant now) {
        Duration ttl = Duration.between(now, expiredAt);
        long seconds = ttl.getSeconds() + (ttl.getNano() > 0 ? 1 : 0);
        return Math.max(1L, seconds);
    }
}
