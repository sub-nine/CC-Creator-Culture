package com.sub9.productservice.category.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CategoryMatchResult.combineWith() 단위 테스트")
class CategoryMatchResultTest {

    @Test
    @DisplayName("Merge는 어떤 다음 결과가 와도 그대로 유지된다")
    void merge_survivesAnyNext() {
        CategoryMatchResult.Merge merge = new CategoryMatchResult.Merge(CategoryHashtagMatchType.ALGORITHM, 0.9);

        assertThat(merge.combineWith(new CategoryMatchResult.NotSimilar())).isEqualTo(merge);
        assertThat(merge.combineWith(new CategoryMatchResult.Failed("실패"))).isEqualTo(merge);
        assertThat(merge.combineWith(new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.AI, 0.7)))
                .isEqualTo(merge);
    }

    @Test
    @DisplayName("다음 결과가 Merge면, 지금까지 뭐였든 Merge로 확정된다")
    void next_mergeAlwaysWins() {
        CategoryMatchResult.Merge merge = new CategoryMatchResult.Merge(CategoryHashtagMatchType.ALGORITHM, 0.9);

        assertThat(new CategoryMatchResult.NotSimilar().combineWith(merge)).isEqualTo(merge);
        assertThat(new CategoryMatchResult.Failed("실패").combineWith(merge)).isEqualTo(merge);
        assertThat(new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.AI, 0.7).combineWith(merge))
                .isEqualTo(merge);
    }

    @Test
    @DisplayName("PendingApproval은 다음 결과가 PendingApproval/NotSimilar/Failed여도 그대로 유지된다")
    void pendingApproval_isStickyAgainstLowerResults() {
        CategoryMatchResult.PendingApproval pendingApproval =
                new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.ALGORITHM, 0.72);
        CategoryMatchResult.PendingApproval otherPendingApproval =
                new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.AI, 0.71);

        assertThat(pendingApproval.combineWith(new CategoryMatchResult.NotSimilar())).isEqualTo(pendingApproval);
        assertThat(pendingApproval.combineWith(new CategoryMatchResult.Failed("실패"))).isEqualTo(pendingApproval);
        assertThat(pendingApproval.combineWith(otherPendingApproval)).isEqualTo(pendingApproval);
    }

    @Test
    @DisplayName("NotSimilar/Failed끼리는 우선순위 없이 다음 결과로 갱신된다")
    void notSimilarAndFailed_haveNoPriorityBetweenThem() {
        CategoryMatchResult.Failed failed = new CategoryMatchResult.Failed("타임아웃");
        CategoryMatchResult.NotSimilar notSimilar = new CategoryMatchResult.NotSimilar();

        assertThat(notSimilar.combineWith(failed)).isEqualTo(failed);
        assertThat(failed.combineWith(notSimilar)).isEqualTo(notSimilar);
    }

    @Test
    @DisplayName("NotSimilar/Failed 다음에 PendingApproval이 오면 PendingApproval로 승격된다")
    void notSimilarOrFailed_upgradeToPendingApproval() {
        CategoryMatchResult.PendingApproval pendingApproval =
                new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.ALGORITHM, 0.72);

        assertThat(new CategoryMatchResult.NotSimilar().combineWith(pendingApproval)).isEqualTo(pendingApproval);
        assertThat(new CategoryMatchResult.Failed("타임아웃").combineWith(pendingApproval)).isEqualTo(pendingApproval);
    }
}
