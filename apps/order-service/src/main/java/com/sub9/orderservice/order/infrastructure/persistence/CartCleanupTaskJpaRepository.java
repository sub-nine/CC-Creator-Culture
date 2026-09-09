package com.sub9.orderservice.order.infrastructure.persistence;

import com.sub9.orderservice.order.domain.model.CartCleanupTask;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartCleanupTaskJpaRepository extends JpaRepository<CartCleanupTask, UUID> {
    @Query("select t from CartCleanupTask t where t.nextAttemptAt <= :now order by t.nextAttemptAt, t.id")
    List<CartCleanupTask> findDue(@Param("now") Instant now, Pageable pageable);

    @Query(value = """
            select * from p_order_cart_cleanup_tasks
             where id = :id and next_attempt_at <= :now
             for update skip locked
            """, nativeQuery = true)
    Optional<CartCleanupTask> findDueForUpdate(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update CartCleanupTask t set t.nextAttemptAt = :nextAttemptAt where t.id = :id")
    int postpone(@Param("id") UUID id, @Param("nextAttemptAt") Instant nextAttemptAt);
}
