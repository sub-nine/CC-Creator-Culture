package com.sub9.productservice.category.domain.entity;

import com.sub9.productservice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_hashtags")
public class Hashtag extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "usage_count", nullable = false)
    private Long usageCount;

    public static Hashtag create(String name) {
        return new Hashtag(name);
    }

    public Hashtag(String name) {
        this.name = name;
        this.usageCount = 0L;
    }

    public void increaseUsageCount() {
        this.usageCount++;
    }

    public void decreaseUsageCount() {
        if (this.usageCount > 0) {
            this.usageCount--;
        }
    }
}