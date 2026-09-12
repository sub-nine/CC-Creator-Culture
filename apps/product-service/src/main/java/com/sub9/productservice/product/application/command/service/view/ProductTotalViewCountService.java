package com.sub9.productservice.product.application.command.service.view;

import com.sub9.productservice.product.application.port.in.view.SyncTotalViewCountsUseCase;
import com.sub9.productservice.product.domain.model.ProductDailyView;
import com.sub9.productservice.product.domain.repository.ProductRepository;
import com.sub9.productservice.product.domain.repository.ProductDailyViewRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductTotalViewCountService implements SyncTotalViewCountsUseCase {
  private final ProductDailyViewRepository dailyViewCommandRepository;
  private final ProductRepository productRepository;

  @Override
  public void syncTotalViewCounts() {
    // TODO : 테이블에 반영되지 않은 조회수는 누락될 것으로 보임 추후 테이블 + Redis 동시 집계하도록 수정 필요 MVP 이후
    //        스케쥴러 동시성도 고려해봐야 함
    List<ProductDailyView> previousDayViewCounts =
        dailyViewCommandRepository.findAllByViewDate(LocalDate.now(Clock.systemUTC()).minusDays(1));

    for (ProductDailyView dailyView : previousDayViewCounts) {
      productRepository.incrementViewCount(
          dailyView.getProductId(), dailyView.getViewCount());
    }
  }
}
