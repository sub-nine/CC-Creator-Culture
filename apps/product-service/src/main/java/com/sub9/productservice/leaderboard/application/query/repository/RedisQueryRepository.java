package com.sub9.productservice.leaderboard.application.query.repository;

import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;

import java.util.List;

public interface RedisQueryRepository {
    List<RankedMember> getRankedMembers(LeaderboardType leaderboardType);
}
