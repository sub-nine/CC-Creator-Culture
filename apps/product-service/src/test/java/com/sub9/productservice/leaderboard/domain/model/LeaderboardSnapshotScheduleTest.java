package com.sub9.productservice.leaderboard.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LeaderboardSnapshotSchedule 단위 테스트")
class LeaderboardSnapshotScheduleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);
    private static final LocalDate TARGET_DATE = TODAY.minusDays(1);

    @Test
    @DisplayName("스냅샷 이력이 없으면 대상 날짜(어제)로 due=true, gap 없음")
    void of_noHistory_dueWithoutGap() {
        LeaderboardSnapshotSchedule schedule = LeaderboardSnapshotSchedule.of(TODAY, null);

        assertThat(schedule.targetDate()).isEqualTo(TARGET_DATE);
        assertThat(schedule.due()).isTrue();
        assertThat(schedule.gapDetected()).isFalse();
    }

    @Test
    @DisplayName("이미 어제 날짜까지 스냅샷이 있으면 due=false")
    void of_alreadySnapshottedYesterday_notDue() {
        LeaderboardSnapshotSchedule schedule = LeaderboardSnapshotSchedule.of(TODAY, TARGET_DATE);

        assertThat(schedule.due()).isFalse();
    }

    @Test
    @DisplayName("마지막 스냅샷이 그제(어제보다 하루 더 이전)면 due=true, gap 없음 - 정상적인 하루 차이")
    void of_oneDayBehind_dueWithoutGap() {
        LeaderboardSnapshotSchedule schedule = LeaderboardSnapshotSchedule.of(TODAY, TARGET_DATE.minusDays(1));

        assertThat(schedule.due()).isTrue();
        assertThat(schedule.gapDetected()).isFalse();
    }

    @Test
    @DisplayName("마지막 스냅샷이 이틀 이상 이전이면 due=true, gap 감지됨")
    void of_moreThanOneDayBehind_dueWithGap() {
        LeaderboardSnapshotSchedule schedule = LeaderboardSnapshotSchedule.of(TODAY, TARGET_DATE.minusDays(3));

        assertThat(schedule.due()).isTrue();
        assertThat(schedule.gapDetected()).isTrue();
    }
}
