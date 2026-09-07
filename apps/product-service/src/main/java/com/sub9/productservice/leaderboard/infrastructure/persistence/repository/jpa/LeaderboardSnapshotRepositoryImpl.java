package com.sub9.productservice.leaderboard.infrastructure.persistence.repository.jpa;

import com.sub9.productservice.leaderboard.application.repository.LeaderboardSnapshotRepository;
import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class LeaderboardSnapshotRepositoryImpl implements LeaderboardSnapshotRepository {

    private final LeaderboardSnapshotJpaRepository jpaRepository;

    @Override
    public List<LeaderboardSnapshot> findLeaderboardSnapshotsByDateRange(
            LeaderboardType type,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return jpaRepository.findByTypeAndDateBetweenAndDeletedAtIsNull(type, startDate, endDate);
    }
}
