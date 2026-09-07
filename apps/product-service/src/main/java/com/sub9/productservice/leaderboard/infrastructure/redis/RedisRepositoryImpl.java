package com.sub9.productservice.leaderboard.infrastructure.redis;

import com.sub9.productservice.leaderboard.application.port.out.RedisRepository;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisRepositoryImpl implements RedisRepository {

    private static final int CATEGORY_KEY_INDEX = 2;
    private static final int HASHTAG_KEY_INDEX = 3;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> incrementScoreIfNotProcessedScript;

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

    @Override
    public boolean incrementScoresIfNotProcessedForOrder(
            UUID orderId,
            Duration ttl,
            Map<UUID, Double> categoryScores,
            Map<UUID, Double> hashtagScores
    ) {
        return applyIfNotProcessed(
                LeaderboardRedisKey.processedOrder(orderId), ttl, categoryScores, hashtagScores);
    }

    @Override
    public boolean incrementScoresIfNotProcessedForProductView(
            UUID eventId,
            Duration ttl,
            Map<UUID, Double> categoryScores,
            Map<UUID, Double> hashtagScores
    ) {
        return applyIfNotProcessed(
                LeaderboardRedisKey.processProductView(eventId), ttl, categoryScores, hashtagScores);
    }

    private boolean applyIfNotProcessed(
            String markerKey,
            Duration ttl,
            Map<UUID, Double> categoryScores,
            Map<UUID, Double> hashtagScores
    ) {
        List<String> keys = List.of(
                markerKey,
                LeaderboardRedisKey.current(LeaderboardType.CATEGORY),
                LeaderboardRedisKey.current(LeaderboardType.HASHTAG)
        );

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(ttl.toSeconds()));
        appendScoreArgs(args, CATEGORY_KEY_INDEX, categoryScores);
        appendScoreArgs(args, HASHTAG_KEY_INDEX, hashtagScores);

        Long applied = redisTemplate.execute(incrementScoreIfNotProcessedScript, keys, args.toArray());
        return Long.valueOf(1L).equals(applied);
    }

    private void appendScoreArgs(List<String> args, int keyIndex, Map<UUID, Double> scores) {
        scores.forEach((targetId, score) -> {
            args.add(String.valueOf(keyIndex));
            args.add(targetId.toString());
            args.add(String.valueOf(score));
        });
    }
}
