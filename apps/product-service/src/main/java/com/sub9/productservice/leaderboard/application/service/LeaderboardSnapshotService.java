package com.sub9.productservice.leaderboard.application.service;

import com.sub9.productservice.leaderboard.application.port.in.TakeLeaderboardSnapshotUseCase;
import com.sub9.productservice.leaderboard.application.port.out.LeaderboardSnapshotRepository;
import com.sub9.productservice.leaderboard.application.port.out.RedisRepository;
import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardSnapshotSchedule;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.domain.model.RankedMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardSnapshotService implements TakeLeaderboardSnapshotUseCase {

    private final RedisRepository redisRepository;
    private final LeaderboardSnapshotRepository leaderboardSnapshotRepository;

    @Override
    public void takeSnapshotIfDue() {
        for (LeaderboardType type : LeaderboardType.values()) {
            snapshotType(type);
        }
    }

    private void snapshotType(LeaderboardType type) {
        LocalDate lastSnapshotDate = leaderboardSnapshotRepository.findMaxDate(type).orElse(null);
        LeaderboardSnapshotSchedule schedule = LeaderboardSnapshotSchedule.of(LocalDate.now(), lastSnapshotDate);

        // 이미 대상 날짜까지 스냅샷이 있으면 이번 tick은 스킵 - 매분 호출돼도 하루에 한 번만 실행됨
        if (!schedule.due()) {
            return;
        }

        // 스케줄러가 하루 이상 못 돈 경우 - Redis ZSET엔 날짜 경계 정보가 없어 그 사이 데이터를
        // 일자별로 못 쪼개고 targetDate 하나로 뭉쳐 저장됨. 정확한 분리는 불가능하니 알림만 남김
        if (schedule.gapDetected()) {
            log.warn("[LEADERBOARD] 스냅샷 gap 감지 - type: {}, 마지막 스냅샷: {}, 이번 대상: {} - 그 사이 데이터가 {}에 뭉쳐서 저장됩니다",
                    type, lastSnapshotDate, schedule.targetDate(), schedule.targetDate());
        }

        // TODO: 아래 saveSnapshots가 실패하면 Redis는 이미 비워진 뒤라 이 날짜 데이터가 유실됨(dual-write)
        List<RankedMember> rankedMembers = redisRepository.snapshotAndClear(type);
        if (rankedMembers.isEmpty()) {
            return;
        }

        saveSnapshots(type, schedule.targetDate(), rankedMembers);
    }

    private void saveSnapshots(LeaderboardType type, LocalDate targetDate, List<RankedMember> rankedMembers) {
        List<LeaderboardSnapshot> snapshots = rankedMembers.stream()
                .map(member -> LeaderboardSnapshot.create(
                        type, member.targetId(), member.score(), member.ranking(), targetDate))
                .toList();
        leaderboardSnapshotRepository.saveAll(snapshots);

        log.info("[LEADERBOARD] 스냅샷 저장 완료 - type: {}, date: {}, 건수: {}", type, targetDate, snapshots.size());
    }
}
