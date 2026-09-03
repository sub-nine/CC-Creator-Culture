package com.sub9.orderservice.order.infrastructure.persistence;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

import com.sub9.orderservice.order.domain.model.OrderCommandRequest;
import com.sub9.orderservice.order.domain.model.OrderCommandType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderCommandRequestJpaRepository
        extends JpaRepository<OrderCommandRequest, UUID> {

    @Modifying
    @Query(value = """
            insert into p_order_command_requests (
                id, actor_id, command_type, idempotency_key, request_hash, status,
                created_at, updated_at
            ) values (
                :id, :actorId, :commandType, :idempotencyKey, :requestHash, 'PROCESSING',
                :startedAt, :startedAt
            )
            on conflict (actor_id, command_type, idempotency_key) do nothing
            """, nativeQuery = true)
    int insertProcessing(
            @Param("id") UUID id,
            @Param("actorId") UUID actorId,
            @Param("commandType") String commandType,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("startedAt") LocalDateTime startedAt);

    Optional<OrderCommandRequest> findByActorIdAndCommandTypeAndIdempotencyKey(
            UUID actorId, OrderCommandType commandType, String idempotencyKey);

    @Lock(PESSIMISTIC_WRITE)
    @Query("select request from OrderCommandRequest request where request.id = :commandRequestId")
    Optional<OrderCommandRequest> findByIdForUpdate(
            @Param("commandRequestId") UUID commandRequestId);
}
