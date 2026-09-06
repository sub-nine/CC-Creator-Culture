package com.sub9.productservice.leaderboard.infrastructure.persistence.query.repository;

import com.sub9.productservice.leaderboard.application.query.repository.RedisQueryRepository;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RedisQueryRepositoryImpl implements RedisQueryRepository {
    @Override
    public List<RankedMember> getRankedMembers(LeaderboardType leaderboardType) {
        return List.of();
    }
}
