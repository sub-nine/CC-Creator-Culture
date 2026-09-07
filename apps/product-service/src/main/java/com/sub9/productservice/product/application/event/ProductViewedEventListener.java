package com.sub9.productservice.product.application.event;

import com.sub9.productservice.product.application.port.ProductViewRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewedEventListener {
  private static final Duration TTL = Duration.ofMinutes(30);
  private final ProductViewRepository productViewRepository;

  @EventListener
  public void handleProductViewedEvent(ProductViewedEvent event) {
    try {
      productViewRepository.recordView(event.productId(), event.viewerId(), TTL);
    } catch (DataAccessException e) {
      log.warn("[FAIL] 상품 조회수 기록 실패. productId = {}",  event.productId(), e);
    }
  }
}
