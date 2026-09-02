package com.sub9.productservice.category.domain.entity;

import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.category.domain.model.CategoryStatus;
import com.sub9.productservice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_categories")
public class Category extends BaseEntity {

    @Column(name = "merged_category_id")
    private UUID mergedCategoryId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CategoryStatus status;

    @Column(name = "unique_version", nullable = false)
    private UUID uniqueVersion;

    @Override
    public void delete(UUID deletedBy) {
        super.delete(deletedBy);

        this.uniqueVersion = this.getId();
    }

    public static Category create(String name, String description) {
        return Category.builder()
                .name(name)
                .description(description)
                .status(CategoryStatus.ACTIVE)
                .uniqueVersion(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .build();
    }

    public CategoryHashtag linkManually(Hashtag hashtag, Double similarity) {
        return CategoryHashtag.create(
                this,
                hashtag,
                CategoryHashtagMatchType.MANUAL,
                CategoryHashtagStatus.MERGED,
                similarity
        );
    }

    public void mergeInto(UUID targetCategoryId) {
        this.mergedCategoryId = targetCategoryId;
        this.status = CategoryStatus.MERGED;
    }

    public void inactivate() {
        this.status = CategoryStatus.INACTIVE;
    }

    public void activate() {
        this.status = CategoryStatus.ACTIVE;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Builder
    private Category(
            UUID mergedCategoryId,
            String name,
            String description,
            CategoryStatus status,
            UUID uniqueVersion
    ) {
        this.mergedCategoryId = mergedCategoryId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.uniqueVersion = uniqueVersion;
    }
}
/*
id	식별자	uuid		PK	NOT NULL		카테고리 식별자	-
merged_category_id	병합된 카테고리 식별자	uuid			-		해당 식별자를 갖는 카테고리로 통합	p_categories
name	카테고리 이름	varchar			NOT NULL		카테고리 이름
description	카테고리 설명	varchar			-		카테고리 설명
status	카테고리 상태	varchar			NOT NULL		카테고리의 상태		ACTIVE, INACTIVE, MERGED
 */