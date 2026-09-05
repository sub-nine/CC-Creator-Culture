package com.sub9.productservice.leaderboard.application.port;

import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;

import java.util.List;

public interface RedisClient {
    List<RankedMember> getRankedMembers(LeaderboardType type);
}
