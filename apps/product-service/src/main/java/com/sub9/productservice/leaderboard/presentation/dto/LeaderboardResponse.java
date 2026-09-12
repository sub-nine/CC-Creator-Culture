package com.sub9.productservice.leaderboard.presentation.dto;

import com.sub9.productservice.leaderboard.domain.model.LeaderboardPeriod;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;

import java.time.LocalDate;
import java.util.List;

public record LeaderboardResponse(
        LeaderboardPeriod period,
        LeaderboardType type,
        LocalDate startDate,
        LocalDate endDate,
        List<LeaderboardItemResponse> items
) {
}
