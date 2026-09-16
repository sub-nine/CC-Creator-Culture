package com.sub9.productservice.leaderboard.application.service;

import com.sub9.productservice.leaderboard.application.port.out.LeaderboardSnapshotRepository;
import com.sub9.productservice.leaderboard.application.port.out.RedisRepository;
import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaderboardSnapshotService 단위 테스트")
class LeaderboardSnapshotServiceTest {

    @Mock
    private RedisRepository redisRepository;
    @Mock
    private LeaderboardSnapshotRepository leaderboardSnapshotRepository;

    private LeaderboardSnapshotService leaderboardSnapshotService;

    @BeforeEach
    void setUp() {
        leaderboardSnapshotService = new LeaderboardSnapshotService(redisRepository, leaderboardSnapshotRepository);
    }

    @Test
    @DisplayName("이미 어제 날짜까지 스냅샷이 있으면 Redis 조회 없이 스킵한다")
    void takeSnapshotIfDue_alreadySnapshottedYesterday_skips() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(leaderboardSnapshotRepository.findMaxDate(any())).thenReturn(Optional.of(yesterday));

        leaderboardSnapshotService.takeSnapshotIfDue();

        verify(redisRepository, never()).snapshotAndClear(any());
        verify(leaderboardSnapshotRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("스냅샷 이력이 없으면 두 타입 모두 어제 날짜로 스냅샷을 저장한다")
    void takeSnapshotIfDue_noHistory_savesBothTypesWithYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        UUID categoryId = UUID.randomUUID();
        UUID hashtagId = UUID.randomUUID();

        when(leaderboardSnapshotRepository.findMaxDate(any())).thenReturn(Optional.empty());
        when(redisRepository.snapshotAndClear(LeaderboardType.CATEGORY))
                .thenReturn(List.of(new RankedMember(1, categoryId, 10.0)));
        when(redisRepository.snapshotAndClear(LeaderboardType.HASHTAG))
                .thenReturn(List.of(new RankedMember(1, hashtagId, 5.0)));

        leaderboardSnapshotService.takeSnapshotIfDue();

        ArgumentCaptor<List<LeaderboardSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(leaderboardSnapshotRepository, org.mockito.Mockito.times(2)).saveAll(captor.capture());

        List<LeaderboardSnapshot> allSaved = captor.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(allSaved).hasSize(2);
        assertThat(allSaved).allSatisfy(snapshot -> assertThat(snapshot.getDate()).isEqualTo(yesterday));
    }

    @Test
    @DisplayName("Redis에 남은 랭킹이 없으면 저장을 호출하지 않는다")
    void takeSnapshotIfDue_emptyRanking_doesNotSave() {
        when(leaderboardSnapshotRepository.findMaxDate(any())).thenReturn(Optional.empty());
        when(redisRepository.snapshotAndClear(any())).thenReturn(List.of());

        leaderboardSnapshotService.takeSnapshotIfDue();

        verify(leaderboardSnapshotRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("마지막 스냅샷이 이틀 이상 전이어도 그 사이 데이터를 대상 날짜 하나로 뭉쳐 저장한다")
    void takeSnapshotIfDue_gapDetected_stillSavesUnderTargetDate() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        LocalDate lastSnapshotDate = targetDate.minusDays(3);
        UUID categoryId = UUID.randomUUID();

        when(leaderboardSnapshotRepository.findMaxDate(any())).thenReturn(Optional.of(lastSnapshotDate));
        when(redisRepository.snapshotAndClear(any()))
                .thenReturn(List.of(new RankedMember(1, categoryId, 42.0)));

        leaderboardSnapshotService.takeSnapshotIfDue();

        ArgumentCaptor<List<LeaderboardSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(leaderboardSnapshotRepository, org.mockito.Mockito.times(2)).saveAll(captor.capture());
        assertThat(captor.getAllValues().stream().flatMap(List::stream))
                .allSatisfy(snapshot -> assertThat(snapshot.getDate()).isEqualTo(targetDate));
    }
}
