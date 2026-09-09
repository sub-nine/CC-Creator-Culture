package com.sub9.productservice.leaderboard.application.port.out;

import com.sub9.productservice.leaderboard.domain.model.LeaderboardEventType;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardScore;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;

import java.util.List;
import java.util.UUID;

public interface RedisRepository {
    List<RankedMember> getRankedMembers(LeaderboardType leaderboardType);

    // eventId가 이미 처리된 적 있으면 아무 것도 하지 않고 false 반환
    boolean incrementScoresIfNotProcessed(
            LeaderboardEventType type,
            UUID keyId,
            List<LeaderboardScore> categoryScores,
            List<LeaderboardScore> hashtagScores
    );
}
