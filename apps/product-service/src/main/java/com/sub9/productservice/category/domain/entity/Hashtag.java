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

    public Hashtag(String name) {
        this.name = name;
        this.usageCount = 0L;
    }
}

/*
id	해시태그 ID	uuid	-	PK	NOT NULL	앱 생성(UUID)	해시태그 식별자	-	주요 엔티티 ID
name	해시태그 이름	varchar			NOT NULL		해시태그 이름	-
usage_count	참조 횟수	bigint	-		NOT NULL	0	해시태그가 사용된(연결된) 누적 횟수	-	리더보드 조회 및 자동완성 정렬용 반정규화 컬럼
 */