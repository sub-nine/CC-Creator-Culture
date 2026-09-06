package com.sub9.productservice.leaderboard.application.repository;

import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;

import java.util.List;

public interface RedisRepository {
    List<RankedMember> getRankedMembers(LeaderboardType leaderboardType);
}
