package com.sub9.productservice.product.application.port.in.view;

import com.sub9.productservice.product.application.command.dto.product.IncrementDailyViewCountsCommand;

public interface IncrementDailyViewCountsUseCase {
  void incrementDailyViewCounts(IncrementDailyViewCountsCommand command);
}
