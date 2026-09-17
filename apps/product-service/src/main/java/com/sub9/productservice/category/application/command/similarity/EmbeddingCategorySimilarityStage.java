package com.sub9.productservice.category.application.command.similarity;

import com.sub9.productservice.category.application.command.port.out.CategoryVectorRepository;
import com.sub9.productservice.category.application.command.port.out.EmbeddingClient;
import com.sub9.productservice.category.application.command.port.out.HashtagVectorRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryMatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// 파이프라인의 2번째 스테이지 - 임베딩 벡터 코사인 유사도 기반 (Levenshtein보다 느림, 신조어/슬랭 등 의미 유사도 커버)
@Order(2)
@Component
@RequiredArgsConstructor
public class EmbeddingCategorySimilarityStage implements CategorySimilarityStage {

    // local/embedding_test_result.md 실험 기준 잠정치 - Levenshtein과 분포가 달라 별도 관리, 운영 데이터로 재보정 필요
    private static final double MERGE_THRESHOLD = 0.50;
    private static final double PENDING_APPROVAL_THRESHOLD = 0.40;

    private final HashtagVectorRepository hashtagVectorRepository;
    private final CategoryVectorRepository categoryVectorRepository;
    private final EmbeddingClient embeddingClient;

    @Override
    public List<CategoryCandidateResult> evaluate(Hashtag hashtag, List<Category> candidates) {
        try {
            ensureHashtagVectorExists(hashtag);
        } catch (Exception e) {
            // 해시태그 벡터 자체를 못 구하면 후보 전체를 판단할 수 없음 - 전부 Failed로 보류
            return candidates.stream()
                    .map(candidate -> new CategoryCandidateResult(candidate, new CategoryMatchResult.Failed(e.getMessage())))
                    .toList();
        }

        List<UUID> candidateIds = candidates.stream().map(Category::getId).toList();
        // 해시태그 벡터 값을 애플리케이션으로 끌고 오지 않고, hashtagId로 DB에서 직접 조인해 유사도 계산
        Map<UUID, Double> similarities = categoryVectorRepository.findSimilarities(hashtag.getId(), candidateIds);

        return candidates.stream()
                .map(candidate -> new CategoryCandidateResult(candidate, judge(candidate, similarities)))
                .toList();
    }

    private void ensureHashtagVectorExists(Hashtag hashtag) {
        if (hashtagVectorRepository.existsByHashtagId(hashtag.getId())) {
            return;
        }
        float[] vector = embeddingClient.embed(hashtag.getName());
        hashtagVectorRepository.saveIfAbsent(hashtag.getId(), vector);
    }

    private CategoryMatchResult judge(Category candidate, Map<UUID, Double> similarities) {
        Double similarity = similarities.get(candidate.getId());
        if (similarity == null) {
            // 카테고리 벡터가 아직 없음(비동기 계산 대기 중) - 비교 자체가 불가능한 상태라 Failed
            return new CategoryMatchResult.Failed("카테고리 벡터 없음");
        }

        if (similarity >= MERGE_THRESHOLD) {
            return new CategoryMatchResult.Merge(CategoryHashtagMatchType.EMBEDDING, similarity);
        }
        if (similarity >= PENDING_APPROVAL_THRESHOLD) {
            return new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.EMBEDDING, similarity);
        }
        return new CategoryMatchResult.NotSimilar();
    }
}
