package com.sub9.orderservice.coupon.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
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
public abstract class CouponBaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // INSERT 시 생성 시각이 전달되지 않으면 데이터베이스의 현재 시각을 기본값으로 사용한다.
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    // INSERT 시 수정 시각이 전달되지 않으면 데이터베이스의 현재 시각을 기본값으로 사용한다.
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at", columnDefinition = "timestamp with time zone")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected CouponBaseEntity(UUID id, UUID createdBy, Instant createdAt) {
        this.id = requireUuidV7(id);
        this.createdBy = Objects.requireNonNull(createdBy, "생성자는 필수입니다.");
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 필수입니다.");
        this.updatedAt = createdAt;
    }

    protected final void updateAudit(UUID updatedBy, Instant updatedAt) {
        this.updatedBy = Objects.requireNonNull(updatedBy, "수정자는 필수입니다.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "수정 시각은 필수입니다.");
    }

    protected final void softDelete(UUID deletedBy, Instant deletedAt) {
        Objects.requireNonNull(deletedBy, "삭제자는 필수입니다.");
        Objects.requireNonNull(deletedAt, "삭제 시각은 필수입니다.");
        if (isDeleted()) {
            return;
        }
        this.deletedBy = deletedBy;
        this.deletedAt = deletedAt;
        updateAudit(deletedBy, deletedAt);
    }

    public final boolean isDeleted() {
        return deletedAt != null;
    }

    private static UUID requireUuidV7(UUID id) {
        Objects.requireNonNull(id, "식별자는 필수입니다.");
        if (id.version() != 7 || id.variant() != 2) {
            throw new IllegalArgumentException("식별자는 UUID v7 형식이어야 합니다.");
        }
        return id;
    }
}
