package com.sub9.productservice.leaderboard.application.port.in;

import com.sub9.productservice.leaderboard.application.model.ProductViewCount;

import java.util.List;
import java.util.UUID;

public interface RecordProductViewScoreUseCase {
    void recordProductViewScore(UUID eventId, List<ProductViewCount> productViewCounts);
}
