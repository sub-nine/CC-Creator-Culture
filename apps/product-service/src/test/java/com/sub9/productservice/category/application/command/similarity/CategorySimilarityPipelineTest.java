package com.sub9.productservice.category.application.command.similarity;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryMatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategorySimilarityPipeline 단위 테스트")
class CategorySimilarityPipelineTest {

    @Mock
    private CategorySimilarityStage firstStage;
    @Mock
    private CategorySimilarityStage secondStage;

    private final Hashtag hashtag = Hashtag.create("아무거나");
    private final Category categoryA = Category.create("카테고리A", null);
    private final Category categoryB = Category.create("카테고리B", null);

    @Test
    @DisplayName("Merge로 확정된 후보는 다음 스테이지로 넘어가지 않고, 다른 후보는 계속 넘어간다")
    void resolve_mergedCandidate_notPassedToNextStage() {
        CategoryMatchResult.Merge merge = new CategoryMatchResult.Merge(CategoryHashtagMatchType.ALGORITHM, 0.9);
        when(firstStage.evaluate(hashtag, List.of(categoryA, categoryB))).thenReturn(List.of(
                new CategoryCandidateResult(categoryA, merge),
                new CategoryCandidateResult(categoryB, new CategoryMatchResult.NotSimilar())
        ));
        when(secondStage.evaluate(eq(hashtag), anyList())).thenReturn(List.of(
                new CategoryCandidateResult(categoryB, new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.EMBEDDING, 0.72))
        ));

        CategorySimilarityPipeline pipeline = new CategorySimilarityPipeline(List.of(firstStage, secondStage));
        List<CategoryCandidateResult> results = pipeline.resolve(hashtag, List.of(categoryA, categoryB));

        verify(secondStage).evaluate(hashtag, List.of(categoryB));
        assertThat(resultFor(results, categoryA)).isEqualTo(merge);
        assertThat(resultFor(results, categoryB)).isInstanceOf(CategoryMatchResult.PendingApproval.class);
    }

    @Test
    @DisplayName("PendingApproval로 확정된 후보는 다음 스테이지로 넘어가지 않고, 그 결과가 그대로 유지된다")
    void resolve_pendingApprovalCandidate_isStickyAndNotReEvaluated() {
        CategoryMatchResult.PendingApproval pendingApproval =
                new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.ALGORITHM, 0.72);
        when(firstStage.evaluate(hashtag, List.of(categoryA, categoryB))).thenReturn(List.of(
                new CategoryCandidateResult(categoryA, pendingApproval),
                new CategoryCandidateResult(categoryB, new CategoryMatchResult.NotSimilar())
        ));
        when(secondStage.evaluate(eq(hashtag), anyList())).thenReturn(List.of(
                new CategoryCandidateResult(categoryB, new CategoryMatchResult.NotSimilar())
        ));

        CategorySimilarityPipeline pipeline = new CategorySimilarityPipeline(List.of(firstStage, secondStage));
        List<CategoryCandidateResult> results = pipeline.resolve(hashtag, List.of(categoryA, categoryB));

        verify(secondStage).evaluate(hashtag, List.of(categoryB));
        assertThat(resultFor(results, categoryA)).isEqualTo(pendingApproval);
        assertThat(resultFor(results, categoryB)).isInstanceOf(CategoryMatchResult.NotSimilar.class);
    }

    @Test
    @DisplayName("한 후보가 앞 스테이지에서 Failed였어도, 뒤 스테이지가 NotSimilar로 결론 내리면 그 결론을 신뢰한다")
    void resolve_laterStageOverridesEarlierFailedForSameCandidate() {
        when(firstStage.evaluate(hashtag, List.of(categoryA)))
                .thenReturn(List.of(new CategoryCandidateResult(categoryA, new CategoryMatchResult.Failed("타임아웃"))));
        when(secondStage.evaluate(hashtag, List.of(categoryA)))
                .thenReturn(List.of(new CategoryCandidateResult(categoryA, new CategoryMatchResult.NotSimilar())));

        CategorySimilarityPipeline pipeline = new CategorySimilarityPipeline(List.of(firstStage, secondStage));
        List<CategoryCandidateResult> results = pipeline.resolve(hashtag, List.of(categoryA));

        assertThat(resultFor(results, categoryA)).isInstanceOf(CategoryMatchResult.NotSimilar.class);
    }

    @Test
    @DisplayName("모든 스테이지가 끝나도 확정되지 않은 후보는 최종적으로 NotSimilar/Failed 등 마지막 결과를 유지한다")
    void resolve_unresolvedAfterAllStages_keepsLastResult() {
        when(firstStage.evaluate(hashtag, List.of(categoryA)))
                .thenReturn(List.of(new CategoryCandidateResult(categoryA, new CategoryMatchResult.NotSimilar())));
        when(secondStage.evaluate(hashtag, List.of(categoryA)))
                .thenReturn(List.of(new CategoryCandidateResult(categoryA, new CategoryMatchResult.NotSimilar())));

        CategorySimilarityPipeline pipeline = new CategorySimilarityPipeline(List.of(firstStage, secondStage));
        List<CategoryCandidateResult> results = pipeline.resolve(hashtag, List.of(categoryA));

        assertThat(resultFor(results, categoryA)).isInstanceOf(CategoryMatchResult.NotSimilar.class);
    }

    @Test
    @DisplayName("스테이지가 하나도 없으면 모든 후보가 NotSimilar로 남는다")
    void resolve_noStages_allCandidatesRemainNotSimilar() {
        CategorySimilarityPipeline pipeline = new CategorySimilarityPipeline(List.of());

        List<CategoryCandidateResult> results = pipeline.resolve(hashtag, List.of(categoryA, categoryB));

        assertThat(results).hasSize(2);
        assertThat(resultFor(results, categoryA)).isInstanceOf(CategoryMatchResult.NotSimilar.class);
        assertThat(resultFor(results, categoryB)).isInstanceOf(CategoryMatchResult.NotSimilar.class);
    }

    @Test
    @DisplayName("후보가 하나도 없으면 빈 목록을 반환하고, 스테이지도 호출하지 않는다")
    void resolve_noCandidates_returnsEmptyListWithoutCallingStages() {
        CategorySimilarityPipeline pipeline = new CategorySimilarityPipeline(List.of(firstStage));

        List<CategoryCandidateResult> results = pipeline.resolve(hashtag, List.of());

        assertThat(results).isEmpty();
        verify(firstStage, never()).evaluate(any(Hashtag.class), anyList());
    }

    private CategoryMatchResult resultFor(List<CategoryCandidateResult> results, Category category) {
        return results.stream()
                .filter(result -> result.category().equals(category))
                .findFirst()
                .orElseThrow()
                .result();
    }
}
