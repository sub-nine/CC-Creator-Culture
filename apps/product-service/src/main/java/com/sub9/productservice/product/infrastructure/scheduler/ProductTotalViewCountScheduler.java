package com.sub9.productservice.product.infrastructure.scheduler;

import com.sub9.productservice.product.application.port.in.view.SyncTotalViewCountsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductTotalViewCountScheduler {
  private final SyncTotalViewCountsUseCase syncTotalViewCountsUseCase;

  @Scheduled(cron = "0 0 0 * * *", zone = "UTC") // 매일 자정 실행
  public void execute() {
    syncTotalViewCountsUseCase.syncTotalViewCounts();
  }
}
