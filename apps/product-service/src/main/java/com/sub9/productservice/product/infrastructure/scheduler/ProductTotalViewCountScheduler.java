package com.sub9.productservice.product.infrastructure.scheduler;

import com.sub9.productservice.product.application.command.service.ProductViewCountCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductTotalViewCountScheduler {
  private final ProductViewCountCommandService viewCountCommandService;

  @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
  public void execute() {
    viewCountCommandService.syncTotalViewCounts();
  }
}
