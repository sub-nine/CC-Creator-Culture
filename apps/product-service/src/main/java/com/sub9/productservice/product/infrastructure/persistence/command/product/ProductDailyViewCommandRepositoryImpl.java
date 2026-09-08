package com.sub9.productservice.product.infrastructure.persistence.command.product;

import com.sub9.productservice.product.domain.model.ProductDailyView;
import com.sub9.productservice.product.domain.repository.ProductDailyViewCommandRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductDailyViewCommandRepositoryImpl implements ProductDailyViewCommandRepository {
  private final ProductDailyViewCommandJPARepository jpaRepository;

  @Override
  public void upsert(UUID id, UUID productId, long viewCount, LocalDate viewDate) {
    jpaRepository.upsert(id, productId, viewCount, viewDate);
  }

  @Override
  public List<ProductDailyView> findAllByViewDate(LocalDate viewDate) {
    return jpaRepository.findAllByViewDate(viewDate);
  }
}
