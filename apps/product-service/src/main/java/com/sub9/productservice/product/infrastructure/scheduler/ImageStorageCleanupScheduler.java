package com.sub9.productservice.product.infrastructure.scheduler;

import com.sub9.productservice.product.application.port.in.image.ProductImageCleanupUseCase;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageStorageCleanupScheduler {
  private final ProductImageCleanupUseCase imageCleanupUseCase;

  @Scheduled(cron = "0 0 3 * * *", zone = "UTC") // 매일 03:00 실행
  public void execute() {
    log.info("[START] 이미지 스토리지 정리");
    imageCleanupUseCase.deleteExpiredImages(Instant.now());
    log.info("[END] 이미지 스토리지 정리");
  }
}
