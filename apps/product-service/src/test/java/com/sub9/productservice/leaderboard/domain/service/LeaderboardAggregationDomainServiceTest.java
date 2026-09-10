package com.sub9.productservice.leaderboard.domain.service;

import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LeaderboardAggregationDomainService 단위 테스트")
class LeaderboardAggregationDomainServiceTest {

    private final LeaderboardAggregationDomainService aggregationDomainService = new LeaderboardAggregationDomainService();

    @Test
    @DisplayName("실시간 랭킹과 기간 스냅샷의 점수를 대상별로 합산해 점수 내림차순으로 재정렬한다")
    void aggregate_mergesCurrentAndSnapshotScoresAndReranks() {
        UUID targetA = UUID.randomUUID();
        UUID targetB = UUID.randomUUID();
        UUID targetC = UUID.randomUUID();

        List<RankedMember> currentRankedMembers = List.of(
                new RankedMember(1, targetA, 10.0),
                new RankedMember(2, targetB, 8.0)
        );
        List<LeaderboardSnapshot> snapshots = List.of(
                LeaderboardSnapshot.create(LeaderboardType.CATEGORY, targetA, 5.0, 1, LocalDate.now()),
                LeaderboardSnapshot.create(LeaderboardType.CATEGORY, targetC, 20.0, 1, LocalDate.now())
        );

        List<RankedMember> result = aggregationDomainService.aggregate(currentRankedMembers, snapshots, 10);

        // targetC: 20.0, targetA: 10.0 + 5.0 = 15.0, targetB: 8.0
        assertThat(result).extracting(RankedMember::targetId).containsExactly(targetC, targetA, targetB);
        assertThat(result).extracting(RankedMember::ranking).containsExactly(1L, 2L, 3L);
        assertThat(result.get(1).score()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("limit을 초과하는 대상은 잘라낸다")
    void aggregate_truncatesToLimit() {
        List<RankedMember> currentRankedMembers = List.of(
                new RankedMember(1, UUID.randomUUID(), 30.0),
                new RankedMember(2, UUID.randomUUID(), 20.0),
                new RankedMember(3, UUID.randomUUID(), 10.0)
        );

        List<RankedMember> result = aggregationDomainService.aggregate(currentRankedMembers, List.of(), 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).score()).isEqualTo(30.0);
        assertThat(result.get(1).score()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("기간 스냅샷이 없어도 실시간 랭킹만으로 정상 집계한다")
    void aggregate_withoutSnapshots_usesOnlyCurrentRanking() {
        UUID targetId = UUID.randomUUID();
        List<RankedMember> currentRankedMembers = List.of(new RankedMember(1, targetId, 7.0));

        List<RankedMember> result = aggregationDomainService.aggregate(currentRankedMembers, List.of(), 10);

        assertThat(result).containsExactly(new RankedMember(1, targetId, 7.0));
    }
}
