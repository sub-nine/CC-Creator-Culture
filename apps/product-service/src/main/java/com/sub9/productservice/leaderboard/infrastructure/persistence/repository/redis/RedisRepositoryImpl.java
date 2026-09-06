package com.sub9.productservice.leaderboard.infrastructure.persistence.repository.redis;

import com.sub9.productservice.leaderboard.application.repository.RedisRepository;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisRepositoryImpl implements RedisRepository {

    private final StringRedisTemplate redisTemplate;

    @Override
    public List<RankedMember> getRankedMembers(LeaderboardType leaderboardType) {
        try {
            String key = LeaderboardRedisKey.current(leaderboardType);
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);

            if (tuples == null) {
                return List.of();
            }

            List<RankedMember> rankedMembers = new ArrayList<>(tuples.size());
            long ranking = 1;
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                rankedMembers.add(new RankedMember(ranking++, UUID.fromString(tuple.getValue()), tuple.getScore()));
            }
            return rankedMembers;
        } catch (DataAccessException exception) {
            log.warn("[REDIS] 리더보드 실시간 랭킹 조회 실패 - type: {}", leaderboardType, exception);
            return List.of();
        }
    }
}
