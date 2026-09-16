package com.sub9.productservice.leaderboard.infrastructure.scheduler;

import com.sub9.productservice.leaderboard.application.port.in.TakeLeaderboardSnapshotUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LeaderboardSnapshotScheduler {
    private final TakeLeaderboardSnapshotUseCase takeLeaderboardSnapshotUseCase;

    @Scheduled(cron = "0 * * * * *", zone = "UTC") // 매 분 실행 - 이미 어제자 스냅샷이 있으면 내부에서 스킵됨
    public void execute() {
        takeLeaderboardSnapshotUseCase.takeSnapshotIfDue();
    }
}
