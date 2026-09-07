package com.sub9.productservice.product.application.command.service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.common.kafka.event.ProductViewSyncEvent;
import com.sub9.productservice.product.application.command.dto.IncrementDailyViewCountsCommand;
import com.sub9.productservice.product.application.port.ProductViewCountPublisher;
import com.sub9.productservice.product.application.port.ProductViewRepository;
import com.sub9.productservice.product.application.query.dto.ProductViewCount;
import com.sub9.productservice.product.domain.model.ProductDailyView;
import com.sub9.productservice.product.domain.repository.ProductCommandRepository;
import com.sub9.productservice.product.domain.repository.ProductDailyViewCommandRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductViewCountCommandService {
  private final ProductDailyViewCommandRepository productDailyViewCommandRepository;
  private final ProductViewCountPublisher productViewCountPublisher;
  private final ProductCommandRepository productCommandRepository;
  private final ProductViewRepository productViewRepository;

  public int syncViewCounts() {
    // TODO : 조회수 유실 가능성 있음 MVP 이후
    //        조회수 반영 -> 삭제 사이 누락은 정합성은 중요도가 낮다 판단되어 일단 MVP에서 제외.
    List<ProductViewCount> productViewCounts = productViewRepository.findAllViewCounts();
    if (productViewCounts.isEmpty()) return 0;

    List<ProductViewSyncEvent.ProductViewCount> eventViewCounts =
        productViewCounts.stream()
            .map(
                productViewCount ->
                    new ProductViewSyncEvent.ProductViewCount(
                        productViewCount.productId(), productViewCount.viewCount()))
            .toList();

    ProductViewSyncEvent event =
        new ProductViewSyncEvent(
            UuidCreator.getTimeOrderedEpoch(), LocalDate.now(Clock.systemUTC()), eventViewCounts);

    productViewCountPublisher.publish(event).thenRun(productViewRepository::deleteAllViewCount);
    return eventViewCounts.size();
  }

  public void incrementDailyViewCounts(IncrementDailyViewCountsCommand command) {
    // TODO : 추후 멱등 처리 필요할 것 같음 MVP 이후
    command
        .viewCounts()
        .forEach(
            viewCount ->
                productDailyViewCommandRepository.upsert(
                    UuidCreator.getTimeOrderedEpoch(),
                    viewCount.productId(),
                    viewCount.viewCount(),
                    command.viewDate()));
  }

  public void syncTotalViewCounts() {
    // TODO : 테이블에 반영되지 않은 조회수는 누락될 것으로 보임 추후 테이블 + Redis 동시 집계하도록 수정 필요 MVP 이후
    //        스케쥴러 동시성도 고려해봐야 함
    List<ProductDailyView> previousDayViewCounts =
        productDailyViewCommandRepository.findAllByViewDate(
            LocalDate.now(Clock.systemUTC()).minusDays(1));

    for (ProductDailyView dailyView : previousDayViewCounts) {
      productCommandRepository.incrementViewCount(
          dailyView.getProductId(), dailyView.getViewCount());
    }
  }
}
