package com.sub9.productservice.leaderboard.application.service;

import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import com.sub9.productservice.leaderboard.application.port.out.CategoryQueryPort;
import com.sub9.productservice.leaderboard.application.port.out.HashtagQueryPort;
import com.sub9.productservice.leaderboard.application.port.out.LeaderboardSnapshotRepository;
import com.sub9.productservice.leaderboard.application.port.out.RedisRepository;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardPeriod;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import com.sub9.productservice.leaderboard.domain.service.LeaderboardAggregationDomainService;
import com.sub9.productservice.leaderboard.presentation.dto.LeaderboardItemResponse;
import com.sub9.productservice.leaderboard.presentation.dto.LeaderboardResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaderboardService 단위 테스트")
class LeaderboardServiceTest {

    @Mock
    private CategoryQueryPort categoryQueryPort;
    @Mock
    private HashtagQueryPort hashtagQueryPort;
    @Mock
    private LeaderboardSnapshotRepository leaderboardSnapshotRepository;
    @Mock
    private LeaderboardAggregationDomainService leaderboardAggregationDomainService;
    @Mock
    private RedisRepository redisRepository;

    private LeaderboardService leaderboardService;

    @BeforeEach
    void setUp() {
        leaderboardService = new LeaderboardService(
                categoryQueryPort, hashtagQueryPort, leaderboardSnapshotRepository,
                leaderboardAggregationDomainService, redisRepository);
    }

    @Test
    @DisplayName("DAILY 조회는 기간 스냅샷 저장소를 조회하지 않고 실시간 랭킹만 집계한다")
    void getCategoryLeaderboard_daily_skipsSnapshotLookup() {
        UUID categoryId = UUID.randomUUID();
        List<RankedMember> currentRankedMembers = List.of(new RankedMember(1, categoryId, 10.0));
        List<RankedMember> aggregated = List.of(new RankedMember(1, categoryId, 10.0));

        when(redisRepository.getRankedMembers(LeaderboardType.CATEGORY)).thenReturn(currentRankedMembers);
        when(leaderboardAggregationDomainService.aggregate(currentRankedMembers, List.of(), 10))
                .thenReturn(aggregated);
        when(categoryQueryPort.getCategoriesByIds(List.of(categoryId)))
                .thenReturn(List.of(new CategoryResponse(categoryId, "패션", null)));

        LeaderboardResponse response = leaderboardService.getCategoryLeaderboard(LeaderboardPeriod.DAILY, 10);

        assertThat(response.period()).isEqualTo(LeaderboardPeriod.DAILY);
        assertThat(response.startDate()).isEqualTo(response.endDate());
        assertThat(response.items()).containsExactly(new LeaderboardItemResponse(1, categoryId, "패션", 10.0));
        verify(leaderboardSnapshotRepository, never())
                .findLeaderboardSnapshotsByDateRange(any(), any(), any());
    }

    @Test
    @DisplayName("WEEKLY 조회는 기간 내 스냅샷 저장소도 함께 조회해 집계한다")
    void getCategoryLeaderboard_weekly_queriesSnapshotRepository() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = LeaderboardPeriod.WEEKLY.getStartDate(today);

        when(redisRepository.getRankedMembers(LeaderboardType.CATEGORY)).thenReturn(List.of());
        when(leaderboardSnapshotRepository.findLeaderboardSnapshotsByDateRange(
                LeaderboardType.CATEGORY, startDate, today))
                .thenReturn(List.of());
        when(leaderboardAggregationDomainService.aggregate(List.of(), List.of(), 10))
                .thenReturn(List.of());
        when(categoryQueryPort.getCategoriesByIds(List.of())).thenReturn(List.of());

        leaderboardService.getCategoryLeaderboard(LeaderboardPeriod.WEEKLY, 10);

        verify(leaderboardSnapshotRepository)
                .findLeaderboardSnapshotsByDateRange(LeaderboardType.CATEGORY, startDate, today);
    }

    @Test
    @DisplayName("집계 결과 중 카테고리 마스터 데이터에서 조회되지 않는 대상은 응답에서 제외한다")
    void getCategoryLeaderboard_excludesTargetsNotFoundInMasterData() {
        UUID existingCategoryId = UUID.randomUUID();
        UUID deletedCategoryId = UUID.randomUUID();
        List<RankedMember> aggregated = List.of(
                new RankedMember(1, existingCategoryId, 10.0),
                new RankedMember(2, deletedCategoryId, 5.0)
        );

        when(redisRepository.getRankedMembers(LeaderboardType.CATEGORY)).thenReturn(List.of());
        when(leaderboardAggregationDomainService.aggregate(any(), any(), eq(10))).thenReturn(aggregated);
        when(categoryQueryPort.getCategoriesByIds(List.of(existingCategoryId, deletedCategoryId)))
                .thenReturn(List.of(new CategoryResponse(existingCategoryId, "패션", null)));

        LeaderboardResponse response = leaderboardService.getCategoryLeaderboard(LeaderboardPeriod.DAILY, 10);

        assertThat(response.items()).extracting(LeaderboardItemResponse::targetId)
                .containsExactly(existingCategoryId);
    }

    @Test
    @DisplayName("해시태그 리더보드 조회는 HASHTAG 타입과 해시태그 조회 포트를 사용한다")
    void getHashtagLeaderboard_usesHashtagTypeAndPort() {
        UUID hashtagId = UUID.randomUUID();
        List<RankedMember> aggregated = List.of(new RankedMember(1, hashtagId, 7.0));

        when(redisRepository.getRankedMembers(LeaderboardType.HASHTAG)).thenReturn(List.of());
        when(leaderboardAggregationDomainService.aggregate(any(), any(), eq(5))).thenReturn(aggregated);
        when(hashtagQueryPort.getHashtagByIds(List.of(hashtagId)))
                .thenReturn(List.of(new HashtagResponse(hashtagId, "스트릿", 3L)));

        LeaderboardResponse response = leaderboardService.getHashtagLeaderboard(LeaderboardPeriod.DAILY, 5);

        assertThat(response.items()).containsExactly(new LeaderboardItemResponse(1, hashtagId, "스트릿", 7.0));
        verify(redisRepository, never()).getRankedMembers(LeaderboardType.CATEGORY);
    }
}
