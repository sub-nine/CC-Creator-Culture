package com.sub9.productservice.product.application.command.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record IncrementDailyViewCountsCommand(
    UUID eventId, LocalDate viewDate, List<ViewCount> viewCounts) {

  public record ViewCount(UUID productId, long viewCount) {}
}
