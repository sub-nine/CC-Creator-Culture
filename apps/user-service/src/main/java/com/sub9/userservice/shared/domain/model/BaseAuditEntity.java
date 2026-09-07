package com.sub9.userservice.shared.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseAuditEntity {

    // INSERT 시 생성 시각이 전달되지 않으면 데이터베이스의 현재 시각을 기본값으로 사용한다.
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "deleted_at", columnDefinition = "timestamp with time zone")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected final void initializeAudit(UUID createdBy, UUID updatedBy, Instant now) {
        this.createdBy = createdBy;
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = createdAt;
    }

    protected final void markDeleted(UUID actorId, Instant now) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        if (isDeleted()) {
            return;
        }

        Instant deletedTime = Objects.requireNonNull(now, "now must not be null");
        this.deletedAt = deletedTime;
        this.deletedBy = actorId;
        this.updatedAt = deletedTime;
        this.updatedBy = actorId;
    }

    public final boolean isDeleted() {
        return deletedAt != null;
    }
}
