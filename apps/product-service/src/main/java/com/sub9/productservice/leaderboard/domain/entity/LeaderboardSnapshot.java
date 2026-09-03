package com.sub9.productservice.leaderboard.domain.entity;

import com.sub9.productservice.common.entity.BaseEntity;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "p_leaderboard_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_leaderboard_snapshots_type_score_ranking",
                columnNames = {"type", "score", "ranking", "date"}
        )
)
public class LeaderboardSnapshot extends BaseEntity {
    @Column(name = "type", nullable = false)
    private LeaderboardType type;

    @Column(name = "type", nullable = false)
    private UUID targetId;

    @Column(name = "type", nullable = false)
    private double score;

    @Column(name = "type", nullable = false)
    private long ranking;

    @Column(name = "type", nullable = false)
    private LocalDate date;
}
/*
id	식별자	uuid	-	PK	NOT NULL	앱 생성(UUID)	리더보드 식별자	-	주요 엔티티 ID
type	리더보드 타입	varchar	-		NOT NULL	-	리더보드 타입		CATEGORY, HASHTAG
target_id	대상 식별자	uuid			NOT NULL		해당 타입의 대상 식별자
score	리더보드 점수	double precision			NOT NULL		리더보드 스코어
ranking	순위	bigint			NOT NULL		리더보드 순위
 */