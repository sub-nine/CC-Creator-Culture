package com.sub9.productservice.leaderboard.presentation.dto;

import java.util.UUID;

public record LeaderboardItemResponse(
        long ranking,
        UUID targetId,
        String name,
        double score
        // TODO: 순위 변동(전기간 대비 증감) 필드 추가
) {
}
