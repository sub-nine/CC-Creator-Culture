package com.sub9.productservice.product.application.command.service.view;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.productservice.product.application.command.dto.product.IncrementDailyViewCountsCommand;
import com.sub9.productservice.product.application.port.in.view.IncrementDailyViewCountsUseCase;
import com.sub9.productservice.product.domain.repository.ProductDailyViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductDailyViewCountService implements IncrementDailyViewCountsUseCase {
  private final ProductDailyViewRepository dailyViewCommandRepository;

  @Override
  public void incrementDailyViewCounts(IncrementDailyViewCountsCommand command) {
    // TODO : 추후 멱등 처리 필요할 것 같음 MVP 이후
    command
        .viewCounts()
        .forEach(
            viewCount ->
                dailyViewCommandRepository.upsert(
                    UuidCreator.getTimeOrderedEpoch(),
                    viewCount.productId(),
                    viewCount.viewCount(),
                    command.viewDate()));
  }
}
