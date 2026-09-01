package com.sub9.userservice.shared.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp")
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "deleted_at", columnDefinition = "timestamp")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected final void initializeAudit(UUID createdBy, UUID updatedBy, Instant now) {
        this.createdBy = createdBy;
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        this.createdAt = toUtc(now);
        this.updatedAt = createdAt;
    }

    protected final void markDeleted(UUID actorId, Instant now) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        if (isDeleted()) {
            return;
        }

        LocalDateTime deletedTime = toUtc(now);
        this.deletedAt = deletedTime;
        this.deletedBy = actorId;
        this.updatedAt = deletedTime;
        this.updatedBy = actorId;
    }

    public final boolean isDeleted() {
        return deletedAt != null;
    }

    protected static LocalDateTime toUtc(Instant instant) {
        // 시간대 정보가 없는 timestamp 컬럼에는 UTC 기준의 날짜와 시각을 저장한다.
        return LocalDateTime.ofInstant(Objects.requireNonNull(instant, "instant must not be null"),
                ZoneOffset.UTC);
    }
}
