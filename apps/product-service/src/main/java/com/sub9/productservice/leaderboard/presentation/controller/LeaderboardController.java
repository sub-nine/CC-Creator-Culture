package com.sub9.productservice.leaderboard.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardPeriod;
import com.sub9.productservice.leaderboard.application.service.LeaderboardQueryService;
import com.sub9.productservice.leaderboard.presentation.dto.LeaderboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaderboards")
@RequiredArgsConstructor
public class LeaderboardController {
    private final LeaderboardQueryService leaderboardQueryService;

    /**
     * 특정 기간 내의 Top N(limit) 카테고리 리더보드 조회
     */
    @GetMapping("/categories")
    public ApiResponse<LeaderboardResponse> getCategoryLeaderboard(
            @RequestParam(defaultValue = "WEEKLY") LeaderboardPeriod period,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.success(leaderboardQueryService.getCategoryLeaderboard(period, limit));
    }

    /**
     * 특정 기간 내의 Top N(limit) 해시태그 리더보드 조회
     */
    @GetMapping("/hashtags")
    public ApiResponse<LeaderboardResponse> getHashtagLeaderboard(
            @RequestParam(defaultValue = "WEEKLY") LeaderboardPeriod period,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.success(leaderboardQueryService.getHashtagLeaderboard(period, limit));
    }
}
