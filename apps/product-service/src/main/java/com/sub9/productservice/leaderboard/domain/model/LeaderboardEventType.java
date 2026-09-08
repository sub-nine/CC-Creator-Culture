package com.sub9.productservice.leaderboard.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

@Getter
@AllArgsConstructor
public enum LeaderboardEventType {
    ORDER_PAID(Duration.ofDays(1), 1.5), // 주문량은 조회수보다 점수를 높게 책정
    PRODUCT_SYNC_VIEW(Duration.ofDays(1), 1.0);

    private final Duration idempotencyTtl;
    // TODO: 점수 가중치 - 임시값
    private final double weight;

}
