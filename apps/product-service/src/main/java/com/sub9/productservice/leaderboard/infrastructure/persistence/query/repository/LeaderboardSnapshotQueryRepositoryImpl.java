package com.sub9.productservice.leaderboard.infrastructure.persistence.query.repository;

import com.sub9.productservice.leaderboard.application.query.repository.LeaderboardSnapshotQueryRepository;
import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class LeaderboardSnapshotQueryRepositoryImpl implements LeaderboardSnapshotQueryRepository {
    @Override
    public List<LeaderboardSnapshot> findLeaderboardSnapshotByDateRange(LocalDate startDate, LocalDate endDate) {
        return List.of();
    }
}
