package com.sub9.productservice.leaderboard.infrastructure.persistence.repository.redis;

import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;

// 리더보드 실시간 랭킹(ZSET)의 Redis 키 형식을 관리하는 유틸리티 클래스
public final class LeaderboardRedisKey {

    private static final String CURRENT_PREFIX = "leaderboard:current:";

    private LeaderboardRedisKey() {}

    public static String current(LeaderboardType type) {
        return CURRENT_PREFIX + type.name().toLowerCase();
    }
}
