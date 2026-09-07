package com.sub9.orderservice.coupon.infrastructure.redis;

import com.sub9.orderservice.coupon.application.event.CouponCreatedEvent;
import com.sub9.orderservice.coupon.application.event.CouponDeletedEvent;
import com.sub9.orderservice.coupon.application.event.CouponUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisCouponRemainingQuantityInitializer {

    private final StringRedisTemplate redisTemplate;

    // 커밋 성공 시에만 실행되며, DB가 롤백되면 Redis 키를 생성하지 않는다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void initialize(CouponCreatedEvent event) {
        store(event.couponId(), event.totalQuantity(), "초기화");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void update(CouponUpdatedEvent event) {
        store(event.couponId(), event.totalQuantity(), "갱신");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void delete(CouponDeletedEvent event) {
        try {
            redisTemplate.delete(CouponRedisKey.remaining(event.couponId()));
        } catch (DataAccessException exception) {
            log.warn("[쿠폰 관리][Redis 수량 삭제 실패] couponId={}", event.couponId());
        }
    }

    private void store(java.util.UUID couponId, int totalQuantity, String operation) {
        try {
            redisTemplate.opsForValue().set(
                    CouponRedisKey.remaining(couponId), Integer.toString(totalQuantity));
        } catch (DataAccessException exception) {
            // DB에는 이미 쿠폰이 커밋됐으므로 Redis 장애를 전파하지 않는다.
            // 누락된 키는 후속 발급 요청의 SET NX 지연 초기화로 복구한다.
            log.warn("[쿠폰 관리][Redis 수량 {} 실패] couponId={} fallback=발급 시 지연 초기화",
                    operation, couponId);
        }
    }
}
