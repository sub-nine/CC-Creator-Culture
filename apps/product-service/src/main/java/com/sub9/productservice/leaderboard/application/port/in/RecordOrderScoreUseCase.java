package com.sub9.productservice.leaderboard.application.port.in;

import com.sub9.productservice.leaderboard.application.model.ProductQuantity;

import java.util.List;
import java.util.UUID;

public interface RecordOrderScoreUseCase {
    void recordOrderScore(UUID orderId, List<ProductQuantity> productQuantities);

    // 이미 ORDER_PAID로 반영된 점수를 취소된 주문 건에 대해 차감한다
    void recordOrderCancellationScore(UUID orderId, List<ProductQuantity> productQuantities);
}
