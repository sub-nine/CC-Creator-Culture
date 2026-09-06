package com.sub9.productservice.leaderboard.infrastructure.persistence;

import com.sub9.productservice.leaderboard.application.repository.LeaderboardSnapshotRepository;
import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class LeaderboardSnapshotRepositoryImpl implements LeaderboardSnapshotRepository {
    @Override
    public List<LeaderboardSnapshot> findLeaderboardSnapshotByDateRange(LeaderboardType type, LocalDate startDate, LocalDate endDate) {
        return List.of();
    }
}
