package com.sub9.productservice.leaderboard.application.repository;

import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;

import java.time.LocalDate;
import java.util.List;

public interface LeaderboardSnapshotRepository {

    List<LeaderboardSnapshot> findLeaderboardSnapshotsByDateRange(LeaderboardType type, LocalDate startDate, LocalDate endDate);
}
