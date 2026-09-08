package com.sub9.productservice.leaderboard.domain.service;

import com.sub9.productservice.leaderboard.domain.model.LeaderboardScore;
import com.sub9.productservice.leaderboard.domain.model.SourceScore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LeaderboardScoreDistributionDomainService {

    // 원본 점수에 가중치를 적용해 targetIdsBySourceId로 연결된 대상(카테고리/해시태그)별로 합산
    public List<LeaderboardScore> distribute(
            List<SourceScore> sourceScores,
            double weight,
            Map<UUID, List<UUID>> targetIdsBySourceId
    ) {
        // 각 Source ID에 대한 가중치 적용 점수 도출
        Map<UUID, Double> sourceScoreMap = sourceScores.stream()
                .map(sourceScore -> sourceScore.weighted(weight))
                .collect(Collectors.toMap(SourceScore::sourceId, SourceScore::score, Double::sum));

        // 가중치 적용된 점수 합산
        Map<UUID, Double> distributedScoreMap = new HashMap<>();
        targetIdsBySourceId.forEach((sourceId, targetIds) -> {
            double score = sourceScoreMap.getOrDefault(sourceId, 0.0);
            targetIds.forEach(targetId -> distributedScoreMap.merge(targetId, score, Double::sum));
        });

        // 합산된 점수를 List<LeaderboardScore> 형태로 반환
        return distributedScoreMap.entrySet().stream()
                .map(entry -> new LeaderboardScore(entry.getKey(), entry.getValue()))
                .toList();
    }
}
