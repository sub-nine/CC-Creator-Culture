package com.sub9.productservice.category.application.command.similarity;

import com.sub9.productservice.category.application.command.port.out.CategoryVectorRepository;
import com.sub9.productservice.category.application.command.port.out.EmbeddingClient;
import com.sub9.productservice.category.application.command.port.out.HashtagVectorRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryMatchResult;
import com.sub9.productservice.category.domain.model.HashtagCategorySimilarityPolicy;
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

    private final HashtagVectorRepository hashtagVectorRepository;
    private final CategoryVectorRepository categoryVectorRepository;
    private final EmbeddingClient embeddingClient;

    @Override
    public List<CategoryCandidateResult> evaluate(Hashtag hashtag, List<Category> candidates) {
        float[] hashtagVector;
        try {
            hashtagVector = resolveHashtagVector(hashtag);
        } catch (Exception e) {
            // 해시태그 벡터 자체를 못 구하면 후보 전체를 판단할 수 없음 - 전부 Failed로 보류
            return candidates.stream()
                    .map(candidate -> new CategoryCandidateResult(candidate, new CategoryMatchResult.Failed(e.getMessage())))
                    .toList();
        }

        List<UUID> candidateIds = candidates.stream().map(Category::getId).toList();
        Map<UUID, Double> similarities = categoryVectorRepository.findSimilarities(hashtagVector, candidateIds);

        return candidates.stream()
                .map(candidate -> new CategoryCandidateResult(candidate, judge(candidate, similarities)))
                .toList();
    }

    private float[] resolveHashtagVector(Hashtag hashtag) {
        return hashtagVectorRepository.findByHashtagId(hashtag.getId())
                .orElseGet(() -> {
                    float[] vector = embeddingClient.embed(hashtag.getName());
                    hashtagVectorRepository.saveIfAbsent(hashtag.getId(), vector);
                    return vector;
                });
    }

    private CategoryMatchResult judge(Category candidate, Map<UUID, Double> similarities) {
        Double similarity = similarities.get(candidate.getId());
        if (similarity == null) {
            // 카테고리 벡터가 아직 없음(비동기 계산 대기 중) - 비교 자체가 불가능한 상태라 Failed
            return new CategoryMatchResult.Failed("카테고리 벡터 없음");
        }

        // TODO: 임계값 재산정 필요 - Levenshtein용 threshold를 그대로 쓰면 안 맞음 (local/embedding_test_result.md 참고)
        if (similarity >= HashtagCategorySimilarityPolicy.MERGE_THRESHOLD) {
            return new CategoryMatchResult.Merge(CategoryHashtagMatchType.AI, similarity);
        }
        if (similarity >= HashtagCategorySimilarityPolicy.PENDING_APPROVAL_THRESHOLD) {
            return new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.AI, similarity);
        }
        return new CategoryMatchResult.NotSimilar();
    }
}
