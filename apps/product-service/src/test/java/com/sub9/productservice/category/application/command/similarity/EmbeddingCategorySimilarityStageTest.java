package com.sub9.productservice.category.application.command.similarity;

import com.sub9.productservice.category.application.command.port.out.CategoryVectorRepository;
import com.sub9.productservice.category.application.command.port.out.EmbeddingClient;
import com.sub9.productservice.category.application.command.port.out.HashtagVectorRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;
import com.sub9.productservice.category.domain.model.CategoryMatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingCategorySimilarityStage 단위 테스트")
class EmbeddingCategorySimilarityStageTest {

    @Mock
    private HashtagVectorRepository hashtagVectorRepository;
    @Mock
    private CategoryVectorRepository categoryVectorRepository;
    @Mock
    private EmbeddingClient embeddingClient;

    private EmbeddingCategorySimilarityStage stage;

    private final Hashtag hashtag = Hashtag.create("아무거나");
    private final Category categoryA = Category.create("카테고리A", null);
    private final Category categoryB = Category.create("카테고리B", null);

    @BeforeEach
    void setUp() {
        stage = new EmbeddingCategorySimilarityStage(hashtagVectorRepository, categoryVectorRepository, embeddingClient);
    }

    @Test
    @DisplayName("해시태그 벡터가 이미 있으면 임베딩 호출 없이 바로 유사도 비교한다")
    void evaluate_hashtagVectorAlreadyExists_skipsEmbedding() {
        when(hashtagVectorRepository.existsByHashtagId(hashtag.getId())).thenReturn(true);
        when(categoryVectorRepository.findSimilarities(hashtag.getId(), List.of(categoryA.getId())))
                .thenReturn(Map.of(categoryA.getId(), 0.9));

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of(categoryA));

        verify(embeddingClient, never()).embed(any());
        verify(hashtagVectorRepository, never()).saveIfAbsent(any(), any());
        assertThat(results.get(0).result()).isInstanceOf(CategoryMatchResult.Merge.class);
    }

    @Test
    @DisplayName("해시태그 벡터가 없으면 임베딩을 계산해서 저장한 뒤 유사도를 비교한다")
    void evaluate_hashtagVectorMissing_embedsAndSaves() {
        float[] vector = {0.1f, 0.2f};
        when(hashtagVectorRepository.existsByHashtagId(hashtag.getId())).thenReturn(false);
        when(embeddingClient.embed(hashtag.getName())).thenReturn(vector);
        when(categoryVectorRepository.findSimilarities(hashtag.getId(), List.of(categoryA.getId())))
                .thenReturn(Map.of(categoryA.getId(), 0.5));

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of(categoryA));

        verify(hashtagVectorRepository).saveIfAbsent(hashtag.getId(), vector);
        assertThat(results.get(0).result()).isInstanceOf(CategoryMatchResult.NotSimilar.class);
    }

    @Test
    @DisplayName("임베딩 계산 자체가 실패하면 후보 전부를 Failed로 반환하고 유사도 조회는 하지 않는다")
    void evaluate_embeddingFails_allCandidatesFailed() {
        when(hashtagVectorRepository.existsByHashtagId(hashtag.getId())).thenReturn(false);
        when(embeddingClient.embed(hashtag.getName())).thenThrow(new RuntimeException("모델 타임아웃"));

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of(categoryA, categoryB));

        assertThat(results).allSatisfy(result ->
                assertThat(result.result()).isInstanceOf(CategoryMatchResult.Failed.class));
        verify(categoryVectorRepository, never()).findSimilarities(any(), anyList());
        verify(hashtagVectorRepository, never()).saveIfAbsent(any(), any());
    }

    @Test
    @DisplayName("특정 후보의 카테고리 벡터가 아직 없으면 그 후보만 Failed고 나머지는 정상 판정한다")
    void evaluate_oneCategoryVectorMissing_onlyThatCandidateFailed() {
        when(hashtagVectorRepository.existsByHashtagId(hashtag.getId())).thenReturn(true);
        when(categoryVectorRepository.findSimilarities(eq(hashtag.getId()), anyList()))
                .thenReturn(Map.of(categoryA.getId(), 0.9)); // categoryB는 결과에서 빠짐

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of(categoryA, categoryB));

        assertThat(resultFor(results, categoryA)).isInstanceOf(CategoryMatchResult.Merge.class);
        assertThat(resultFor(results, categoryB)).isInstanceOf(CategoryMatchResult.Failed.class);
    }

    @Test
    @DisplayName("유사도가 MERGE_THRESHOLD 이상이면 Merge로 판정한다")
    void judge_similarityAboveMergeThreshold_returnsMerge() {
        when(hashtagVectorRepository.existsByHashtagId(hashtag.getId())).thenReturn(true);
        when(categoryVectorRepository.findSimilarities(hashtag.getId(), List.of(categoryA.getId())))
                .thenReturn(Map.of(categoryA.getId(), 0.8));

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of(categoryA));

        assertThat(results.get(0).result()).isInstanceOf(CategoryMatchResult.Merge.class);
    }

    @Test
    @DisplayName("유사도가 PENDING_APPROVAL_THRESHOLD 이상 MERGE_THRESHOLD 미만이면 PendingApproval로 판정한다")
    void judge_similarityBetweenThresholds_returnsPendingApproval() {
        when(hashtagVectorRepository.existsByHashtagId(hashtag.getId())).thenReturn(true);
        when(categoryVectorRepository.findSimilarities(hashtag.getId(), List.of(categoryA.getId())))
                .thenReturn(Map.of(categoryA.getId(), 0.7));

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of(categoryA));

        assertThat(results.get(0).result()).isInstanceOf(CategoryMatchResult.PendingApproval.class);
    }

    @Test
    @DisplayName("유사도가 PENDING_APPROVAL_THRESHOLD 미만이면 NotSimilar로 판정한다")
    void judge_similarityBelowPendingThreshold_returnsNotSimilar() {
        when(hashtagVectorRepository.existsByHashtagId(hashtag.getId())).thenReturn(true);
        when(categoryVectorRepository.findSimilarities(hashtag.getId(), List.of(categoryA.getId())))
                .thenReturn(Map.of(categoryA.getId(), 0.1));

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of(categoryA));

        assertThat(results.get(0).result()).isInstanceOf(CategoryMatchResult.NotSimilar.class);
    }

    @Test
    @DisplayName("후보가 하나도 없으면 빈 목록을 반환하고, 어떤 조회도 하지 않는다")
    void evaluate_noCandidates_returnsEmptyList() {
        when(hashtagVectorRepository.existsByHashtagId(hashtag.getId())).thenReturn(true);
        when(categoryVectorRepository.findSimilarities(hashtag.getId(), List.of())).thenReturn(Map.of());

        List<CategoryCandidateResult> results = stage.evaluate(hashtag, List.of());

        assertThat(results).isEmpty();
    }

    private CategoryMatchResult resultFor(List<CategoryCandidateResult> results, Category category) {
        return results.stream()
                .filter(result -> result.category().equals(category))
                .findFirst()
                .orElseThrow()
                .result();
    }
}
