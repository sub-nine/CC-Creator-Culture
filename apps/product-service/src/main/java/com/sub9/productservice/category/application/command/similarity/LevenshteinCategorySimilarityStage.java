package com.sub9.productservice.category.application.command.similarity;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryMatchResult;
import com.sub9.productservice.category.domain.model.HashtagCategorySimilarityPolicy;
import com.sub9.productservice.category.domain.service.LevenshteinSimilarityDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

// 파이프라인의 1번째 스테이지 - Levenshtein Distance 기반 문자열 유사도(빠름)
@Order(1)
@Component
@RequiredArgsConstructor
public class LevenshteinCategorySimilarityStage implements CategorySimilarityStage {

    private final LevenshteinSimilarityDomainService levenshteinSimilarityDomainService;

    @Override
    public List<CategoryCandidateResult> evaluate(Hashtag hashtag, List<Category> candidates) {
        return candidates.stream()
                .map(candidate -> new CategoryCandidateResult(candidate, judge(hashtag, candidate)))
                .toList();
    }

    private CategoryMatchResult judge(Hashtag hashtag, Category candidate) {
        double similarity = levenshteinSimilarityDomainService.calculateSimilarity(candidate.getName(), hashtag.getName());

        if (similarity >= HashtagCategorySimilarityPolicy.MERGE_THRESHOLD) {
            return new CategoryMatchResult.Merge(CategoryHashtagMatchType.ALGORITHM, similarity);
        }
        if (similarity >= HashtagCategorySimilarityPolicy.PENDING_APPROVAL_THRESHOLD) {
            return new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.ALGORITHM, similarity);
        }
        return new CategoryMatchResult.NotSimilar();
    }
}
