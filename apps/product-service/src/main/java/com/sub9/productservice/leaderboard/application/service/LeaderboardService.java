package com.sub9.productservice.leaderboard.application.service;

import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import com.sub9.productservice.leaderboard.application.port.CategoryQueryPort;
import com.sub9.productservice.leaderboard.application.port.HashtagQueryPort;
import com.sub9.productservice.leaderboard.application.repository.LeaderboardSnapshotRepository;
import com.sub9.productservice.leaderboard.application.repository.RedisRepository;
import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardPeriod;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import com.sub9.productservice.leaderboard.domain.service.LeaderboardAggregationDomainService;
import com.sub9.productservice.leaderboard.presentation.dto.LeaderboardItemResponse;
import com.sub9.productservice.leaderboard.presentation.dto.LeaderboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderboardService {
    private final CategoryQueryPort categoryQueryPort;
    private final HashtagQueryPort hashtagQueryPort;
    private final LeaderboardSnapshotRepository leaderboardSnapshotRepository;
    private final LeaderboardAggregationDomainService leaderboardAggregationDomainService;
    private final RedisRepository redisRepository;

    public LeaderboardResponse getCategoryLeaderboard(LeaderboardPeriod period, int limit) {
        // TODO: 리더보드 조회 로직 다형성 방식으로 전환 필요

        LocalDate now = LocalDate.now();
        LocalDate startDate = period.getStartDate(now);
        LocalDate endDate = period.getEndDate(now);

        List<RankedMember> aggregatedRankedMembers =
                fetchAndAggregateRankedMembers(LeaderboardType.CATEGORY, startDate, endDate, limit);

        // 집계된 대상들의 이름 조회
        Map<UUID, String> namesById = getCategoryNamesById(aggregatedRankedMembers);

        // 응답 item으로 변환
        List<LeaderboardItemResponse> items = toItems(aggregatedRankedMembers, namesById);

        // 응답 조립
        return new LeaderboardResponse(period, startDate, endDate, items);
    }

    public LeaderboardResponse getHashtagLeaderboard(LeaderboardPeriod period, int limit) {
        LocalDate now = LocalDate.now();
        LocalDate startDate = period.getStartDate(now);
        LocalDate endDate = period.getEndDate(now);

        List<RankedMember> aggregatedRankedMembers =
                fetchAndAggregateRankedMembers(LeaderboardType.HASHTAG, startDate, endDate, limit);

        // 집계된 대상들의 이름 조회
        Map<UUID, String> namesById = getHashtagNamesById(aggregatedRankedMembers);

        // 응답 item으로 변환
        List<LeaderboardItemResponse> items = toItems(aggregatedRankedMembers, namesById);

        // 응답 조립
        return new LeaderboardResponse(period, startDate, endDate, items);
    }

    private List<RankedMember> fetchAndAggregateRankedMembers(
            LeaderboardType leaderboardType,
            LocalDate startDate,
            LocalDate endDate,
            int limit
    ) {
        // 오늘자 실시간 랭킹(redis) + 기간 내 스냅샷(DB) 조회
        List<RankedMember> currentRankedMembers = redisRepository.getRankedMembers(leaderboardType);
        List<LeaderboardSnapshot> periodLeaderboardSnapshots = startDate.equals(endDate) ? List.of() :
                leaderboardSnapshotRepository.findLeaderboardSnapshotByDateRange(
                        leaderboardType,
                        startDate,
                        endDate
                );

        // 둘을 합산 및 재정렬하여 기간 리더보드로 집계
        return leaderboardAggregationDomainService.aggregate(currentRankedMembers, periodLeaderboardSnapshots, limit);
    }

    private Map<UUID, String> getCategoryNamesById(List<RankedMember> rankedMembers) {
        List<UUID> targetIds = rankedMembers.stream()
                .map(RankedMember::targetId)
                .toList();

        return categoryQueryPort.getCategoriesByIds(targetIds).stream()
                .collect(Collectors.toMap(CategoryResponse::id, CategoryResponse::name));
    }

    private Map<UUID, String> getHashtagNamesById(List<RankedMember> rankedMembers) {
        List<UUID> targetIds = rankedMembers.stream()
                .map(RankedMember::targetId)
                .toList();

        return hashtagQueryPort.getHashtagByIds(targetIds).stream()
                .collect(Collectors.toMap(HashtagResponse::id, HashtagResponse::name));
    }

    // 랭킹엔 있지만 마스터 데이터에서 조회되지 않는 대상(삭제/비활성화 등)은 응답에서 제외
    private List<LeaderboardItemResponse> toItems(List<RankedMember> rankedMembers, Map<UUID, String> namesById) {
        return rankedMembers.stream()
                .filter(member -> namesById.containsKey(member.targetId()))
                .map(member -> new LeaderboardItemResponse(
                        member.ranking(),
                        member.targetId(),
                        namesById.get(member.targetId()),
                        member.score()
                ))
                .toList();
    }
}
