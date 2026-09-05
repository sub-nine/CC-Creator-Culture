package com.sub9.orderservice.common.entity;

import com.sub9.orderservice.common.persistence.InstantTimestampConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Convert(converter = InstantTimestampConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp")
    private Instant createdAt;

    @LastModifiedDate
    @Convert(converter = InstantTimestampConverter.class)
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp")
    private Instant updatedAt;

    protected BaseEntity(UUID id) {
        this.id = requireUuidV7(id);
    }

    private static UUID requireUuidV7(UUID id) {
        Objects.requireNonNull(id, "식별자는 필수입니다.");
        if (id.version() != 7 || id.variant() != 2) {
            throw new IllegalArgumentException("식별자는 UUID v7 형식이어야 합니다.");
        }
        return id;
    }
}
