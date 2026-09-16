package com.sub9.productservice.category.application.command.port.out;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;

import java.util.List;

// 유사도 판단 파이프라인의 스테이지 하나. 순수 계산(Levenshtein)이든 외부 호출(임베딩/LLM)이든
// 파이프라인 입장에서는 동일하게 다뤄진다 - 기술적인 구현은 이 포트 뒤에 완전히 숨겨진다.
// candidates로 받은 후보 각각에 대해 판정을 내려 반환한다(후보 하나당 결과 하나)
public interface CategorySimilarityStage {

    List<CategoryCandidateResult> evaluate(Hashtag hashtag, List<Category> candidates);
}
