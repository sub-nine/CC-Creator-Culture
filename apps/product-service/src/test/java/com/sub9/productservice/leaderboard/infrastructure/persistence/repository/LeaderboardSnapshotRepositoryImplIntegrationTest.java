package com.sub9.productservice.leaderboard.infrastructure.persistence.repository;

import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@SpringBootTest
@DisplayName("LeaderboardSnapshotRepositoryImpl - 통합 테스트")
class LeaderboardSnapshotRepositoryImplIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LeaderboardSnapshotRepositoryImpl leaderboardSnapshotRepository;

    @Autowired
    private LeaderboardSnapshotJpaRepository leaderboardSnapshotJpaRepository;

    @Test
    @DisplayName("타입과 날짜 범위에 해당하는 스냅샷만 조회한다")
    void findLeaderboardSnapshotsByDateRange_filtersByTypeAndDateRange() {
        LocalDate today = LocalDate.now();
        UUID categoryId = UUID.randomUUID();
        UUID hashtagId = UUID.randomUUID();

        leaderboardSnapshotJpaRepository.save(
                LeaderboardSnapshot.create(LeaderboardType.CATEGORY, categoryId, 10.0, 1, today.minusDays(1)));
        leaderboardSnapshotJpaRepository.save(
                LeaderboardSnapshot.create(LeaderboardType.CATEGORY, categoryId, 20.0, 1, today.minusDays(10)));
        leaderboardSnapshotJpaRepository.save(
                LeaderboardSnapshot.create(LeaderboardType.HASHTAG, hashtagId, 5.0, 1, today.minusDays(1)));

        List<LeaderboardSnapshot> results = leaderboardSnapshotRepository.findLeaderboardSnapshotsByDateRange(
                LeaderboardType.CATEGORY, today.minusDays(6), today);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTargetId()).isEqualTo(categoryId);
        assertThat(results.get(0).getScore()).isEqualTo(10.0);
    }
}
