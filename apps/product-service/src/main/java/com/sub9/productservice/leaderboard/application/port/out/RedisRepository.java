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

    // 현재 랭킹을 원자적으로 읽고 같은 키를 비움(스냅샷 저장 후 하루 단위로 리셋하기 위함)
    List<RankedMember> snapshotAndClear(LeaderboardType leaderboardType);
}
