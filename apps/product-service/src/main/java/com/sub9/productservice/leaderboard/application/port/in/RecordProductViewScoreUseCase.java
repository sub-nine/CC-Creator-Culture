package com.sub9.productservice.leaderboard.application.port.in;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface RecordProductViewScoreUseCase {
    void recordProductViewScore(LocalDate localDate, Map<UUID, Long> productViewCounts);
}
