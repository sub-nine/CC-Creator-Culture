package com.sub9.productservice.product.application.command.service.view;

import com.sub9.productservice.product.application.port.in.view.SyncTotalViewCountsUseCase;
import com.sub9.productservice.product.domain.model.ProductDailyView;
import com.sub9.productservice.product.domain.repository.ProductDailyViewRepository;
import com.sub9.productservice.product.domain.repository.ProductRepository;
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
    LocalDate today = LocalDate.now(Clock.systemUTC());

    List<ProductDailyView> pendingViewCounts =
        dailyViewCommandRepository.findAllByViewDateBeforeAndAggregatedFalse(today);

    for (ProductDailyView dailyView : pendingViewCounts) {
      if (!dailyViewCommandRepository.tryMarkAsAggregated(dailyView.getId())) {
        continue;
      }

      productRepository.incrementViewCount(dailyView.getProductId(), dailyView.getViewCount());
    }
  }
}
