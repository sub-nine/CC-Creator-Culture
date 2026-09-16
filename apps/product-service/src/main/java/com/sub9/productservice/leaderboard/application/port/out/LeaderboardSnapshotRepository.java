package com.sub9.productservice.leaderboard.application.port.out;

import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaderboardSnapshotRepository {

    List<LeaderboardSnapshot> findLeaderboardSnapshotsByDateRange(LeaderboardType type, LocalDate startDate, LocalDate endDate);

    // 마지막으로 스냅샷이 저장된 날짜 - 스냅샷을 아직 한 번도 안 찍었으면 빈 값
    Optional<LocalDate> findMaxDate(LeaderboardType type);

    List<LeaderboardSnapshot> saveAll(List<LeaderboardSnapshot> snapshots);
}
