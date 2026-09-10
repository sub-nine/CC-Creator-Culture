package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxEvent;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, UUID> {
    // TODO: 지금은 동시성 안전하지 않음 - FOR UPDATE SKIP LOCKED로 claim하는 쿼리로 교체 필요
    List<OutboxEvent> findByStatusOrderByCreatedAt(OutboxStatus status, Pageable pageable);
}
