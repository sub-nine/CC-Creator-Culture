package com.sub9.orderservice.order.domain.model;

import com.sub9.orderservice.common.entity.BaseEntity;
import com.sub9.orderservice.common.persistence.InstantTimestampConverter;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "p_order_command_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_command_actor_type_key",
                columnNames = {"actor_id", "command_type", "idempotency_key"}),
        indexes = @Index(name = "idx_order_command_order_id", columnList = "order_id"),
        check = {
                @CheckConstraint(name = "ck_order_command_type", constraint = "command_type in ('CREATE_ORDER', 'CANCEL_ORDER')"),
                @CheckConstraint(name = "ck_order_command_status", constraint = "status in ('PROCESSING', 'SUCCEEDED', 'FAILED')"),
                @CheckConstraint(
                        name = "ck_order_command_request_hash",
                        constraint = "request_hash ~ '^[0-9a-f]{64}$'"),
                @CheckConstraint(
                        name = "ck_order_command_completion",
                        constraint = "(status = 'PROCESSING' and response_status is null and response_payload is null and completed_at is null)"
                                + " or (status = 'SUCCEEDED' and response_status between 200 and 299 and response_payload is not null and completed_at is not null)"
                                + " or (status = 'FAILED' and response_status between 400 and 599 and response_payload is not null and completed_at is not null)")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCommandRequest extends BaseEntity {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, updatable = false, length = 30)
    private OrderCommandType commandType;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, columnDefinition = "char(64)")
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderCommandStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_id",
            foreignKey = @ForeignKey(name = "fk_order_command_requests_order"))
    @Getter(AccessLevel.NONE)
    private Order order;

    @Column(name = "response_status", columnDefinition = "smallint")
    private Short responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Convert(converter = InstantTimestampConverter.class)
    @Column(name = "completed_at", columnDefinition = "timestamp")
    private Instant completedAt;

    private OrderCommandRequest(UUID id, UUID actorId, OrderCommandType commandType,
            IdempotencyKey idempotencyKey, String requestHash) {
        super(id);
        this.actorId = Objects.requireNonNull(actorId, "요청자 식별자는 필수입니다.");
        this.commandType = Objects.requireNonNull(commandType, "주문 명령 종류는 필수입니다.");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "멱등 키는 필수입니다.").value();
        this.requestHash = requireRequestHash(requestHash);
        this.status = OrderCommandStatus.PROCESSING;
    }

    public static OrderCommandRequest start(UUID id, UUID actorId, OrderCommandType commandType,
            IdempotencyKey idempotencyKey, String requestHash) {
        return new OrderCommandRequest(id, actorId, commandType, idempotencyKey, requestHash);
    }

    public UUID getOrderId() {
        return order == null ? null : order.getId();
    }

    public boolean hasSameRequestHash(String candidate) {
        return requestHash.equals(candidate);
    }

    public boolean isProcessing() {
        return status == OrderCommandStatus.PROCESSING;
    }

    public void completeSuccess(Order order, int httpStatus, String responsePayload, Instant completedAt) {
        Objects.requireNonNull(order, "완료된 주문은 필수입니다.");
        complete(OrderCommandStatus.SUCCEEDED, order, httpStatus, responsePayload, completedAt);
    }

    public void completeFailure(Order order, int httpStatus, String responsePayload, Instant completedAt) {
        complete(OrderCommandStatus.FAILED, order, httpStatus, responsePayload, completedAt);
    }

    private void complete(OrderCommandStatus completedStatus, Order order, int httpStatus,
            String responsePayload, Instant completedAt) {
        if (!isProcessing()) {
            throw new IllegalStateException("완료된 주문 명령은 다시 변경할 수 없습니다.");
        }
        validateHttpStatus(completedStatus, httpStatus);
        if (responsePayload == null || responsePayload.isBlank()) {
            throw new IllegalArgumentException("재응답할 응답 본문은 필수입니다.");
        }
        this.status = completedStatus;
        this.order = order;
        this.responseStatus = (short) httpStatus;
        this.responsePayload = responsePayload;
        this.completedAt = Objects.requireNonNull(completedAt, "명령 완료 시각은 필수입니다.");
    }

    private static String requireRequestHash(String requestHash) {
        Objects.requireNonNull(requestHash, "요청 해시는 필수입니다.");
        if (!SHA_256.matcher(requestHash).matches()) {
            throw new IllegalArgumentException("요청 해시는 SHA-256 소문자 16진수 형식이어야 합니다.");
        }
        return requestHash;
    }

    private static void validateHttpStatus(OrderCommandStatus status, int httpStatus) {
        boolean valid = status == OrderCommandStatus.SUCCEEDED
                ? httpStatus >= 200 && httpStatus <= 299
                : httpStatus >= 400 && httpStatus <= 599;
        if (!valid) {
            throw new IllegalArgumentException("명령 결과와 HTTP 상태가 일치하지 않습니다.");
        }
    }
}
