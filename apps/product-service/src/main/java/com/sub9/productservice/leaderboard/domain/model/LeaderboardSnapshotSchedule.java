package com.sub9.productservice.leaderboard.domain.model;

import java.time.LocalDate;

// 오늘(today) 기준 스냅샷 대상 날짜(어제)를 계산하고, 마지막 스냅샷 날짜와 비교해
// 이번에 찍어야 하는지(due)/그 사이 며칠치가 뭉쳐 저장되는지(gapDetected)를 판단하는 정책
public record LeaderboardSnapshotSchedule(
        LocalDate targetDate,
        boolean due,
        boolean gapDetected
) {
    public static LeaderboardSnapshotSchedule of(LocalDate today, LocalDate lastSnapshotDate) {
        LocalDate targetDate = today.minusDays(1);
        boolean due = lastSnapshotDate == null || lastSnapshotDate.isBefore(targetDate);
        boolean gapDetected = lastSnapshotDate != null && lastSnapshotDate.isBefore(targetDate.minusDays(1));
        return new LeaderboardSnapshotSchedule(targetDate, due, gapDetected);
    }
}
