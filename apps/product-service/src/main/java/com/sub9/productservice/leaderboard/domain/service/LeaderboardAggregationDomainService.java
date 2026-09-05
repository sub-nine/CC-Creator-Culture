package com.sub9.productservice.leaderboard.domain.service;

import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeaderboardAggregationDomainService {
    public List<RankedMember> aggregate(List<RankedMember> currentRankedMembers, List<LeaderboardSnapshot> periodLeaderboardSnapshots, int limit) {
        return null;
    }
}
