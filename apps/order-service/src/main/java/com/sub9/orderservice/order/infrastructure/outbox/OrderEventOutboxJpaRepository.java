package com.sub9.orderservice.order.infrastructure.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderEventOutboxJpaRepository extends JpaRepository<OrderEventOutbox, UUID> {
    // 여러 발행기가 동시에 실행돼도 다른 트랜잭션이 잠근 기록은 건너뛰어 같은 기록을 함께 점유하지 않는다.
    @Query(value = """
            select * from p_order_event_outbox
             where next_attempt_at <= :now
             order by next_attempt_at, id
             limit :limit
             for update skip locked
            """, nativeQuery = true)
    List<OrderEventOutbox> findDueForUpdate(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query("update OrderEventOutbox e set e.nextAttemptAt = :nextAttemptAt where e.id in :ids")
    int postpone(@Param("ids") List<UUID> ids, @Param("nextAttemptAt") Instant nextAttemptAt);

    @Query("select min(e.createdAt) from OrderEventOutbox e")
    Instant findOldestCreatedAt();
}
