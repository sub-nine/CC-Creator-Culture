package com.sub9.productservice.category.application.command.similarity;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryMatchResult;
import com.sub9.productservice.category.domain.service.LevenshteinSimilarityDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LevenshteinCategorySimilarityStage 단위 테스트")
class LevenshteinCategorySimilarityStageTest {

    private LevenshteinCategorySimilarityStage stage;

    @BeforeEach
    void setUp() {
        stage = new LevenshteinCategorySimilarityStage(new LevenshteinSimilarityDomainService());
    }

    @Test
    @DisplayName("유사도가 MERGE_THRESHOLD 이상인 후보는 Merge로 판정한다")
    void evaluate_similarityAboveMergeThreshold_returnsMerge() {
        Category category = Category.create("강아지", null);
        Hashtag hashtag = Hashtag.create("강아지");

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of(category));

        assertThat(results).hasSize(1);
        CategoryCandidateResult candidateResult = results.get(0);
        assertThat(candidateResult.category()).isEqualTo(category);
        assertThat(candidateResult.result()).isInstanceOf(CategoryMatchResult.Merge.class);
        CategoryMatchResult.Merge merge = (CategoryMatchResult.Merge) candidateResult.result();
        assertThat(merge.matchType()).isEqualTo(CategoryHashtagMatchType.ALGORITHM);
        assertThat(merge.similarity()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("유사도가 PENDING_APPROVAL_THRESHOLD 이상 MERGE_THRESHOLD 미만인 후보는 PendingApproval로 판정한다")
    void evaluate_similarityBetweenThresholds_returnsPendingApproval() {
        // "강아지용" vs "강아지몸" - 4글자 중 1글자만 달라 유사도 1 - 1/4 = 0.75
        Category category = Category.create("강아지용", null);
        Hashtag hashtag = Hashtag.create("강아지몸");

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of(category));

        assertThat(results).hasSize(1);
        CategoryMatchResult result = results.get(0).result();
        assertThat(result).isInstanceOf(CategoryMatchResult.PendingApproval.class);
        assertThat(((CategoryMatchResult.PendingApproval) result).matchType()).isEqualTo(CategoryHashtagMatchType.ALGORITHM);
    }

    @Test
    @DisplayName("모든 후보와의 유사도가 PENDING_APPROVAL_THRESHOLD 미만이면 NotSimilar로 판정한다")
    void evaluate_similarityBelowAllThresholds_returnsNotSimilar() {
        Category category = Category.create("전자기기", null);
        Hashtag hashtag = Hashtag.create("수제비누");

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of(category));

        assertThat(results.get(0).result()).isInstanceOf(CategoryMatchResult.NotSimilar.class);
    }

    @Test
    @DisplayName("후보 카테고리가 비어있으면 빈 목록을 반환한다")
    void evaluate_noCandidates_returnsEmptyList() {
        Hashtag hashtag = Hashtag.create("아무거나");

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of());

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("여러 후보를 각각 독립적으로 판정한다 - 후보마다 다른 결과가 나올 수 있다")
    void evaluate_multipleCandidates_judgesEachIndependently() {
        Category lessSimilar = Category.create("강아쥐용품", null);
        Category mostSimilar = Category.create("강아지", null);
        Hashtag hashtag = Hashtag.create("강아지");

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of(lessSimilar, mostSimilar));

        assertThat(results).hasSize(2);
        CategoryCandidateResult mostSimilarResult = results.stream()
                .filter(r -> r.category().equals(mostSimilar))
                .findFirst().orElseThrow();
        assertThat(mostSimilarResult.result()).isInstanceOf(CategoryMatchResult.Merge.class);

        CategoryCandidateResult lessSimilarResult = results.stream()
                .filter(r -> r.category().equals(lessSimilar))
                .findFirst().orElseThrow();
        assertThat(lessSimilarResult.result()).isInstanceOf(CategoryMatchResult.NotSimilar.class);
    }
}
