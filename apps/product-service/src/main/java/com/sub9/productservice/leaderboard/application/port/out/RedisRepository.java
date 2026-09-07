package com.sub9.productservice.leaderboard.application.port.out;

import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RedisRepository {
    List<RankedMember> getRankedMembers(LeaderboardType leaderboardType);

    // orderId가 이미 처리된 적 있으면 아무 것도 하지 않고 false 반환
    boolean incrementScoresIfNotProcessedForOrder(
            UUID orderId,
            Duration ttl,
            Map<UUID, Double> categoryScores,
            Map<UUID, Double> hashtagScores
    );

    // eventId가 이미 처리된 적 있으면 아무 것도 하지 않고 false 반환
    boolean incrementScoresIfNotProcessedForProductView(
            UUID eventId,
            Duration ttl,
            Map<UUID, Double> categoryScores,
            Map<UUID, Double> hashtagScores
    );
}
