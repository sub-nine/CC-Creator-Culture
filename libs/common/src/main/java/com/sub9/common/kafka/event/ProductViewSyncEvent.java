package com.sub9.common.kafka.event;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProductViewSyncEvent(
    UUID eventId, LocalDate viewDate, List<ProductViewCount> productViewCounts) {

  public record ProductViewCount(UUID productId, long viewCount) {}
}
