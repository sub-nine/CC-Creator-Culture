package com.sub9.productservice.category.domain.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HashtagCategorySimilarityPolicy {
    // 자동 병합(MERGED)으로 간주할 최소 유사도
    public static final double MERGE_THRESHOLD = 0.8;
    // 승인 대기(PENDING_APPROVAL)로 간주할 최소 유사도
    public static final double PENDING_APPROVAL_THRESHOLD = 0.7;

}
