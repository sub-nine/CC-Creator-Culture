package com.sub9.productservice.leaderboard.domain.model;

import java.util.UUID;

public record RankedMember(
        long ranking,
        UUID targetId,
        double score
) {
}
