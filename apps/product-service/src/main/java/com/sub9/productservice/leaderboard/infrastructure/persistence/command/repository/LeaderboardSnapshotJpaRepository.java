package com.sub9.productservice.leaderboard.infrastructure.persistence.command.repository;

import com.sub9.productservice.leaderboard.domain.entity.LeaderboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LeaderboardSnapshotJpaRepository extends JpaRepository<LeaderboardSnapshot, UUID> {
}
