package com.sub9.productservice.leaderboard.application.port.in;

import java.util.UUID;

public interface RecordOrderScoreUseCase {
    void recordOrderScore(UUID orderId, UUID productId);
}
