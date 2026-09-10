package com.sub9.productservice.leaderboard.domain.service;

import com.sub9.productservice.leaderboard.domain.model.LeaderboardScore;
import com.sub9.productservice.leaderboard.domain.model.SourceScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("LeaderboardScoreDistributionDomainService 단위 테스트")
class LeaderboardScoreDistributionDomainServiceTest {

    private final LeaderboardScoreDistributionDomainService distributionDomainService =
            new LeaderboardScoreDistributionDomainService();

    @Test
    @DisplayName("가중치를 적용한 원본 점수를 연결된 대상들에게 합산해서 분배한다")
    void distribute_appliesWeightAndSumsScoresByTarget() {
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();
        UUID categoryX = UUID.randomUUID();
        UUID categoryY = UUID.randomUUID();

        List<SourceScore> sourceScores = List.of(
                new SourceScore(productA, 2.0),
                new SourceScore(productB, 3.0)
        );

        // productA -> categoryX, categoryY / productB -> categoryX
        Map<UUID, List<UUID>> targetIdsBySourceId = Map.of(
                productA, List.of(categoryX, categoryY),
                productB, List.of(categoryX)
        );

        List<LeaderboardScore> result = distributionDomainService.distribute(sourceScores, 1.5, targetIdsBySourceId);

        assertThat(result).extracting(LeaderboardScore::targetId, LeaderboardScore::score)
                .containsExactlyInAnyOrder(
                        tuple(categoryX, 2.0 * 1.5 + 3.0 * 1.5),
                        tuple(categoryY, 2.0 * 1.5)
                );
    }

    @Test
    @DisplayName("연결된 대상이 없는 소스는 결과에 포함되지 않는다")
    void distribute_sourceWithoutTargets_isExcluded() {
        UUID productWithoutCategory = UUID.randomUUID();
        List<SourceScore> sourceScores = List.of(new SourceScore(productWithoutCategory, 5.0));

        List<LeaderboardScore> result = distributionDomainService.distribute(sourceScores, 1.0, Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("같은 소스가 여러 번 주어지면 가중치 적용 점수를 먼저 합산한 뒤 분배한다")
    void distribute_duplicateSourceScores_areSummedBeforeDistribution() {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        List<SourceScore> sourceScores = List.of(
                new SourceScore(productId, 2.0),
                new SourceScore(productId, 3.0)
        );
        Map<UUID, List<UUID>> targetIdsBySourceId = Map.of(productId, List.of(categoryId));

        List<LeaderboardScore> result = distributionDomainService.distribute(sourceScores, 2.0, targetIdsBySourceId);

        assertThat(result).containsExactly(new LeaderboardScore(categoryId, 10.0));
    }
}
