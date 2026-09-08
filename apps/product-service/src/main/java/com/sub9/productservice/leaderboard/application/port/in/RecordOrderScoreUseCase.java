package com.sub9.productservice.leaderboard.application.port.in;

import com.sub9.productservice.leaderboard.application.model.ProductQuantity;

import java.util.List;
import java.util.UUID;

public interface RecordOrderScoreUseCase {
    void recordOrderScore(UUID orderId, List<ProductQuantity> productQuantities);
}
