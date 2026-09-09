package com.sub9.productservice.leaderboard.domain.model;

import java.util.UUID;

public record LeaderboardScore(UUID targetId, double score) {
}
