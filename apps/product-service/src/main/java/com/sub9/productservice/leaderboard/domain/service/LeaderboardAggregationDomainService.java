package com.sub9.productservice.leaderboard.domain.service;

import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LeaderboardAggregationDomainService {

    public List<RankedMember> aggregate(
            List<RankedMember> currentRankedMembers,
            List<LeaderboardSnapshot> periodLeaderboardSnapshots,
            int limit
    ) {
        Map<UUID, Double> scoreByTargetId = new HashMap<>();
        currentRankedMembers.forEach(
                member -> scoreByTargetId.merge(member.targetId(), member.score(), Double::sum));
        periodLeaderboardSnapshots.forEach(
                snapshot -> scoreByTargetId.merge(snapshot.getTargetId(), snapshot.getScore(), Double::sum));

        List<Map.Entry<UUID, Double>> topEntries = scoreByTargetId.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .toList();

        List<RankedMember> aggregatedRankedMembers = new ArrayList<>(topEntries.size());
        for (int i = 0; i < topEntries.size(); i++) {
            Map.Entry<UUID, Double> entry = topEntries.get(i);
            aggregatedRankedMembers.add(new RankedMember(i + 1L, entry.getKey(), entry.getValue()));
        }
        return aggregatedRankedMembers;
    }
}
