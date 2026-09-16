package com.sub9.productservice.leaderboard.application.port.in;

public interface TakeLeaderboardSnapshotUseCase {

    // 아직 어제자 스냅샷이 없는 타입에 한해 현재 랭킹을 스냅샷으로 저장하고 실시간 랭킹을 초기화
    void takeSnapshotIfDue();
}
