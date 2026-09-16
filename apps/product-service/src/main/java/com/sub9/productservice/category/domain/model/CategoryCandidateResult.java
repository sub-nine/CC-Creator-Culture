package com.sub9.productservice.category.domain.model;

import com.sub9.productservice.category.domain.entity.Category;

// 후보 카테고리 하나와 그 카테고리에 대한 파이프라인의 최종 판정을 짝지은 값
public record CategoryCandidateResult(Category category, CategoryMatchResult result) {
}
