package com.sub9.productservice.category.application.command.service;

import com.sub9.productservice.category.application.command.port.out.CategorySimilarityStage;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;
import com.sub9.productservice.category.domain.model.CategoryMatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CategorySimilarityPipeline {

    // 순서는 각 구현체의 @Order로 결정 - 스테이지를 추가/제거/재정렬해도 이 클래스는 안 바뀜
    private final List<CategorySimilarityStage> stages;

    public List<CategoryCandidateResult> resolve(Hashtag hashtag, List<Category> candidates) {
        Map<UUID, CategoryMatchResult> accumulated = initializeAccumulated(candidates);

        // 아직 확정 안 된(=다음 스테이지로 넘겨야 할) 후보만 담음
        List<Category> remaining = new ArrayList<>(candidates);
        for (CategorySimilarityStage stage : stages) {
            // 남은 후보가 없으면 뒤 스테이지는 호출할 필요 없음
            if (remaining.isEmpty()) {
                break;
            }
            remaining = runStage(stage, hashtag, remaining, accumulated);
        }

        return candidates.stream().map(candidate -> new CategoryCandidateResult(
                        candidate, accumulated.get(candidate.getId()
                ))).toList();
    }

    // 후보별 누적 결과의 초기값은 전부 NotSimilar
    private Map<UUID, CategoryMatchResult> initializeAccumulated(List<Category> candidates) {
        Map<UUID, CategoryMatchResult> accumulated = new HashMap<>();
        for (Category candidate : candidates) {
            accumulated.put(candidate.getId(), new CategoryMatchResult.NotSimilar());
        }
        return accumulated;
    }

    // 이번 스테이지를 remaining 후보들에 대해 실행해 accumulated를 갱신하고, 다음 스테이지로 넘길 후보만 반환
    private List<Category> runStage(
            CategorySimilarityStage stage,
            Hashtag hashtag,
            List<Category> remaining,
            Map<UUID, CategoryMatchResult> accumulated
    ) {
        // 스테이지 Evaluate
        List<CategoryCandidateResult> evaluateResults = stage.evaluate(hashtag, remaining);

        // Evaluate 결과를 Category Id로 인덱싱
        Map<UUID, CategoryMatchResult> stageResultsById = indexByCategoryId(evaluateResults);

        List<Category> stillUnresolved = new ArrayList<>();
        for (Category candidate : remaining) {
            CategoryMatchResult stageResult = stageResultsById
                    .getOrDefault(candidate.getId(), new CategoryMatchResult.NotSimilar());
            // 우선순위 규칙(Merge 최우선/PendingApproval 고정/나머지 자유 갱신)은 combineWith()에 캡슐화
            CategoryMatchResult combined = accumulated.get(candidate.getId()).combineWith(stageResult);
            accumulated.put(candidate.getId(), combined);

            // Merge/PendingApproval로 확정된 후보는 다음 스테이지로 안 넘김(비용 절감)
            boolean resolved = combined instanceof CategoryMatchResult.Merge
                    || combined instanceof CategoryMatchResult.PendingApproval;
            if (!resolved) {
                stillUnresolved.add(candidate);
            }
        }
        return stillUnresolved;
    }

    // 카테고리 ID로 바로 조회할 수 있게 인덱싱 (스테이지가 순서를 보장하지 않아도 됨)
    private Map<UUID, CategoryMatchResult> indexByCategoryId(List<CategoryCandidateResult> stageResults) {
        Map<UUID, CategoryMatchResult> stageResultsById = new HashMap<>();
        for (CategoryCandidateResult stageResult : stageResults) {
            stageResultsById.put(stageResult.category().getId(), stageResult.result());
        }
        return stageResultsById;
    }
}
