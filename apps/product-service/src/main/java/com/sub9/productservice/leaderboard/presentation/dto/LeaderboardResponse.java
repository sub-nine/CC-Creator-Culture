package com.sub9.productservice.leaderboard.presentation.dto;

import com.sub9.productservice.leaderboard.application.model.LeaderboardPeriod;

import java.time.LocalDate;
import java.util.List;

public record LeaderboardResponse(
        LeaderboardPeriod period,
        LocalDate startDate,
        LocalDate endDate,
        List<LeaderboardItemResponse> items
) {
}
