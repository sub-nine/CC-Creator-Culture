package com.sub9.productservice.product.infrastructure.scheduler;

import com.sub9.productservice.product.application.port.in.view.PublishProductViewCountsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewCountScheduler {
  private final PublishProductViewCountsUseCase publishProductViewCountsUseCase;

  @Scheduled(cron = "0 * * * * *", zone = "UTC") // 매 분 실행
  public void execute() {
    int productCount = publishProductViewCountsUseCase.syncViewCounts();
    if (productCount > 0) log.info("상품 조회수 집계 요청. 상품 수 = {}개", productCount);
  }
}
