package com.sub9.productservice.leaderboard.application.service;

import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import com.sub9.productservice.leaderboard.application.model.LeaderboardPeriod;
import com.sub9.productservice.leaderboard.application.port.CategoryQueryPort;
import com.sub9.productservice.leaderboard.application.port.HashtagQueryPort;
import com.sub9.productservice.leaderboard.application.port.RedisClient;
import com.sub9.productservice.leaderboard.application.repository.LeaderboardSnapshotQueryRepository;
import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import com.sub9.productservice.leaderboard.domain.service.LeaderboardAggregationDomainService;
import com.sub9.productservice.leaderboard.presentation.dto.LeaderboardItemResponse;
import com.sub9.productservice.leaderboard.presentation.dto.LeaderboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderboardService {
    private final CategoryQueryPort categoryQueryPort;
    private final HashtagQueryPort hashtagQueryPort;
    // TODO: LeaderboardSnapshotQueryRepository 구현 필요
    private final LeaderboardSnapshotQueryRepository leaderboardSnapshotQueryRepository;
    // TODO: LeaderboardAggregationDomainService 구현 필요
    private final LeaderboardAggregationDomainService leaderboardAggregationDomainService;
    // TODO: RedisClient 구현필요
    private final RedisClient redisClient;

    @Transactional(readOnly = true)
    public LeaderboardResponse getCategoryLeaderboard(LeaderboardPeriod period, int limit) {
        // period를 조회 기간(날짜 range)으로 변환
        LocalDate now = LocalDate.now();
        LocalDate startDate = period.getStartDate(now);
        LocalDate endDate = period.getEndDate(now);

        // 오늘자 실시간 랭킹(redis) + 기간 내 스냅샷(DB) 조회
        List<RankedMember> currentRankedMembers = redisClient.getRankedMembers(LeaderboardType.CATEGORY);
        List<LeaderboardSnapshot> periodLeaderboardSnapshots =
                leaderboardSnapshotQueryRepository.findLeaderboardSnapshotByDateRange(startDate, endDate);

        // 둘을 합산 및 재정렬하여 기간 리더보드로 집계
        List<RankedMember> aggregatedRankedMembers =
                leaderboardAggregationDomainService.aggregate(currentRankedMembers, periodLeaderboardSnapshots, limit);

        // 집계된 대상들의 정보 조회
        Map<UUID, CategoryResponse> categoriesById = getCategoriesById(aggregatedRankedMembers);

        // 랭킹엔 있지만 카테고리 마스터 데이터에서 조회되지 않는 대상(삭제/비활성화 등)은 응답에서 제외
        List<LeaderboardItemResponse> items = aggregatedRankedMembers.stream()
                .filter(member -> categoriesById.containsKey(member.targetId()))
                .map(member -> new LeaderboardItemResponse(
                        member.ranking(),
                        member.targetId(),
                        categoriesById.get(member.targetId()).name(),
                        member.score()
                ))
                .toList();

        // 응답 조립
        return new LeaderboardResponse(period, startDate, endDate, items);
    }

    public LeaderboardResponse getHashtagLeaderboard(LeaderboardPeriod period, int limit) {
        // period를 조회 기간(날짜 range)으로 변환
        LocalDate now = LocalDate.now();
        LocalDate startDate = period.getStartDate(now);
        LocalDate endDate = period.getEndDate(now);

        // 오늘자 실시간 랭킹(redis) + 기간 내 스냅샷(DB) 조회
        List<RankedMember> currentRankedMembers = redisClient.getRankedMembers(LeaderboardType.HASHTAG);
        List<LeaderboardSnapshot> periodLeaderboardSnapshots =
                leaderboardSnapshotQueryRepository.findLeaderboardSnapshotByDateRange(startDate, endDate);

        // 둘을 합산 및 재정렬하여 기간 리더보드로 집계
        List<RankedMember> aggregatedRankedMembers =
                leaderboardAggregationDomainService.aggregate(currentRankedMembers, periodLeaderboardSnapshots, limit);

        // 집계된 대상들의 정보 조회
        Map<UUID, HashtagResponse> categoriesById = getHashtagsById(aggregatedRankedMembers);

        // 랭킹엔 있지만 카테고리 마스터 데이터에서 조회되지 않는 대상(삭제/비활성화 등)은 응답에서 제외
        List<LeaderboardItemResponse> items = aggregatedRankedMembers.stream()
                .filter(member -> categoriesById.containsKey(member.targetId()))
                .map(member -> new LeaderboardItemResponse(
                        member.ranking(),
                        member.targetId(),
                        categoriesById.get(member.targetId()).name(),
                        member.score()
                ))
                .toList();

        // 응답 조립
        return new LeaderboardResponse(period, startDate, endDate, items);
    }

    private Map<UUID, CategoryResponse> getCategoriesById(
            List<RankedMember> rankedMembers
    ) {
        List<UUID> targetIds = rankedMembers.stream()
                .map(RankedMember::targetId)
                .toList();

        return categoryQueryPort.getCategoriesByIds(targetIds).stream()
                .collect(Collectors.toMap(CategoryResponse::id, Function.identity()));
    }

    private Map<UUID, HashtagResponse> getHashtagsById(
            List<RankedMember> rankedMembers
    ) {
        List<UUID> targetIds = rankedMembers.stream()
                .map(RankedMember::targetId)
                .toList();

        return hashtagQueryPort.getHashtagByIds(targetIds).stream()
                .collect(Collectors.toMap(HashtagResponse::id, Function.identity()));
    }
}
