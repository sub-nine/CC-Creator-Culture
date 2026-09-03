package com.sub9.productservice.leaderboard.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.leaderboard.application.model.LeaderboardPeriod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaderboards")
public class LeaderboardController {

    /**
     * 특정 기간 내의 Top N(limit) 카테고리 리더보드 조회
     */
    @GetMapping("/categories")
    public ApiResponse<Object> getCategoryLeaderboard(
            @RequestParam LeaderboardPeriod period,
            @RequestParam int limit
    ) {
        // TODO: 카테고리 리더보드 조회 서비스 연동
        return ApiResponse.success(null);
    }

    /**
     * 특정 기간 내의 Top N(limit) 해시태그 리더보드 조회
     */
    @GetMapping("/hashtags")
    public ApiResponse<Object> getHashtagLeaderboard(
            @RequestParam LeaderboardPeriod period,
            @RequestParam int limit
    ) {
        // TODO: 해시태그 리더보드 조회 서비스 연동
        return ApiResponse.success(null);
    }
}
