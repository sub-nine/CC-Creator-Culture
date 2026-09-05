package com.sub9.productservice.category.domain.entity;

import com.sub9.productservice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "p_hashtags",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_hashtags_name",
                        columnNames = {"name", "unique_version"}
                )
        }
)
public class Hashtag extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "usage_count", nullable = false)
    private Long usageCount;

    @Column(name = "unique_version", nullable = false)
    private UUID uniqueVersion;

    @Override
    public void delete(UUID deletedBy) {
        super.delete(deletedBy);

        this.uniqueVersion = this.getId();
    }

    public static Hashtag create(String name) {
        return Hashtag.builder()
                .name(name)
                .uniqueVersion(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .build();
    }

    public void increaseUsageCount() {
        this.usageCount++;
    }

    public void decreaseUsageCount() {
        if (this.usageCount > 0) {
            this.usageCount--;
        }
    }

    @Builder
    private Hashtag(String name, UUID uniqueVersion) {
        this.name = name;
        this.usageCount = 0L;
        this.uniqueVersion = uniqueVersion;
    }
}