package com.sub9.productservice.leaderboard.infrastructure.redis;

import com.sub9.productservice.leaderboard.domain.model.LeaderboardEventType;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;

import java.util.UUID;

// 리더보드 실시간 랭킹(ZSET)의 Redis 키 형식을 관리하는 유틸리티 클래스
public final class LeaderboardRedisKey {

    private static final String CURRENT_PREFIX = "leaderboard:current:";
    private static final String PROCESSED_ORDER_PREFIX = "leaderboard:processed:order:paid:";
    private static final String PROCESSED_PRODUCT_VIEW_PREFIX = "leaderboard:processed:product:viewed:";

    public static String current(LeaderboardType type) {
        return CURRENT_PREFIX + type.name().toLowerCase();
    }

    public static String processed(LeaderboardEventType type, UUID id) {
        return switch (type) {
            case LeaderboardEventType.ORDER_PAID -> PROCESSED_ORDER_PREFIX + id;
            case LeaderboardEventType.PRODUCT_SYNC_VIEW -> PROCESSED_PRODUCT_VIEW_PREFIX + id;
        };
    }
}
