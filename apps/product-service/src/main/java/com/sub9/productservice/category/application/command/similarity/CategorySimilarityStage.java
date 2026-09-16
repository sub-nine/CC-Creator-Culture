package com.sub9.productservice.category.application.command.similarity;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;

import java.util.List;

// 유사도 판단 파이프라인의 스테이지, 기술적인 구현은 추상화
// candidates로 받은 병합 후보 각각에 대해 판정을 내려 반환한다(후보 하나당 결과 하나)
public interface CategorySimilarityStage {

    List<CategoryCandidateResult> evaluate(Hashtag hashtag, List<Category> candidates);
}
