package com.sub9.productservice.leaderboard.infrastructure.persistence.repository;

import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaderboardSnapshotJpaRepository extends JpaRepository<LeaderboardSnapshot, UUID> {
    List<LeaderboardSnapshot> findByTypeAndDateBetweenAndDeletedAtIsNull(LeaderboardType type, LocalDate startDate, LocalDate endDate);
}
