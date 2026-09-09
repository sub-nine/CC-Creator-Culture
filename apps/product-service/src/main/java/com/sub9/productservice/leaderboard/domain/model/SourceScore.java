package com.sub9.productservice.leaderboard.domain.model;

import java.util.UUID;

public record SourceScore(
        UUID sourceId,
        double score
) {
    public SourceScore weighted(double weight) {
        return new SourceScore(sourceId, score * weight);
    }
}
