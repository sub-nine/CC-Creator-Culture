package com.sub9.productservice.leaderboard.application.repository;

import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;

import java.time.LocalDate;
import java.util.List;

public interface LeaderboardSnapshotQueryRepository {

    List<LeaderboardSnapshot> findLeaderboardSnapshotByDateRange(LocalDate startDate, LocalDate endDate);
}
