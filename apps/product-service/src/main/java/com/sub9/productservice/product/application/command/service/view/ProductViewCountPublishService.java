package com.sub9.productservice.product.application.command.service.view;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.common.kafka.event.ProductViewSyncEvent;
import com.sub9.productservice.product.application.port.in.view.PublishProductViewCountsUseCase;
import com.sub9.productservice.product.application.port.out.product.ProductViewCountPublisher;
import com.sub9.productservice.product.application.port.out.product.ProductViewRepository;
import com.sub9.productservice.product.application.query.dto.ProductViewCount;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductViewCountPublishService implements PublishProductViewCountsUseCase {
  private final ProductViewCountPublisher productViewCountPublisher;
  private final ProductViewRepository productViewRepository;

  @Override
  public int syncViewCounts() {
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
}
