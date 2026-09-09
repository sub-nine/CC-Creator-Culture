package com.sub9.orderservice.order.domain.repository;

import com.sub9.orderservice.order.domain.model.CartCleanupTask;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartCleanupTaskRepository {
    CartCleanupTask save(CartCleanupTask task);
    List<CartCleanupTask> findDue(Instant now, int limit);
    Optional<CartCleanupTask> findDueForUpdate(UUID id, Instant now);
    void delete(CartCleanupTask task);
    void postpone(UUID id, Instant nextAttemptAt);
}
