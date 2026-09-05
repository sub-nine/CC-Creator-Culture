package com.sub9.productservice.leaderboard.application.model;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@RequiredArgsConstructor
public enum LeaderboardPeriod {
    DAILY(0),
    WEEKLY(6),
    MONTHLY(29);

    private final long daysBack;

    public LocalDate getStartDate(LocalDate today) {
        return today.minusDays(daysBack);
    }

    public LocalDate getEndDate(LocalDate today) {
        return today;
    }
}
