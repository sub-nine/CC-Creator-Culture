package com.sub9.orderservice.order.infrastructure.persistence;

import com.sub9.orderservice.order.domain.model.CartCleanupTask;
import com.sub9.orderservice.order.domain.repository.CartCleanupTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CartCleanupTaskRepositoryAdapter implements CartCleanupTaskRepository {
    private final CartCleanupTaskJpaRepository repository;

    @Override
    public CartCleanupTask save(CartCleanupTask task) {
        return repository.save(task);
    }

    @Override
    public List<CartCleanupTask> findDue(Instant now, int limit) {
        return repository.findDue(now, PageRequest.of(0, limit));
    }

    @Override
    public Optional<CartCleanupTask> findDueForUpdate(UUID id, Instant now) {
        return repository.findDueForUpdate(id, now);
    }

    @Override
    public void delete(CartCleanupTask task) {
        repository.delete(task);
    }

    @Override
    public void postpone(UUID id, Instant nextAttemptAt) {
        repository.postpone(id, nextAttemptAt);
    }
}
