package com.sub9.productservice.category.domain.entity;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.category.domain.exception.CategoryErrorCode;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "p_categories_hashtags",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_categories_hashtags_category_hashtag",
                        columnNames = {"category_id", "hashtag_id"}
                )
        }
)
public class CategoryHashtag extends BaseEntity {
    @JoinColumn(name = "category_id", nullable = false)
    @ManyToOne
    private Category category;

    @JoinColumn(name = "hashtag_id", nullable = false)
    @ManyToOne
    private Hashtag hashtag;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false)
    private CategoryHashtagMatchType matchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CategoryHashtagStatus status;

    @Column(name = "similarity_score", precision = 5, scale = 4, nullable = false)
    private BigDecimal similarityScore;

    @Column(name = "unique_version", nullable = false)
    private UUID uniqueVersion;

    @Override
    public void delete(UUID deletedBy) {
        super.delete(deletedBy);

        this.uniqueVersion = this.getId();
    }

    public static CategoryHashtag create(
            Category category,
            Hashtag hashtag,
            CategoryHashtagMatchType matchType,
            CategoryHashtagStatus status,
            BigDecimal similarityScore
    ) {
        return CategoryHashtag.builder()
                .category(category)
                .hashtag(hashtag)
                .matchType(matchType)
                .status(status)
                .similarityScore(similarityScore)
                .uniqueVersion(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .build();
    }

    public void approve() {
        if (this.status != CategoryHashtagStatus.PENDING_APPROVAL) {
            throw new BusinessException(CategoryErrorCode.NOT_PENDING_APPROVAL);
        }
        this.status = CategoryHashtagStatus.MERGED;
    }

    public void reject() {
        if (this.status != CategoryHashtagStatus.PENDING_APPROVAL) {
            throw new BusinessException(CategoryErrorCode.NOT_PENDING_APPROVAL);
        }
        this.status = CategoryHashtagStatus.REJECTED;
    }

    @Builder
    private CategoryHashtag(
            Category category,
            Hashtag hashtag,
            CategoryHashtagMatchType matchType,
            CategoryHashtagStatus status,
            BigDecimal similarityScore,
            UUID uniqueVersion
    ) {
        this.category = category;
        this.hashtag = hashtag;
        this.matchType = matchType;
        this.status = status;
        this.similarityScore = similarityScore;
        this.uniqueVersion = uniqueVersion;
    }
}
/*
id	식별자	uuid		PK	NOT NULL	앱 생성(UUID)	식별자		주요 엔티티 ID
hashtag_id	해시태그 ID	uuid	-		NOT NULL		해시태그 식별자	p_hashtags
category_id	카테고리 ID	uuid			NOT NULL		연결된 카테고리 식별자	p_categories
match_type	매핑 종류	varchar			NOT NULL		맵핑된 방식에 대한 정의	-	AI, ALGORITHM, MANUAL, PROMOTED(카테고리 신규 생성)
status	병합 상태	varchar			NOT NULL		카테고리로의 병합 상태	-	PENDING, ANALYIZNG, PENDING_APPROVAL, MERGED, REJECTED
similarity_score	유사도	numeric(5, 4)			NOT NULL		카테고리, 해시태그 유사도	-	0~1 사이의 실수
 */