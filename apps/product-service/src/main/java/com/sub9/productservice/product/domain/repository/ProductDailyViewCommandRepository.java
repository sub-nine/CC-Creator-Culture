package com.sub9.productservice.product.domain.repository;

import com.sub9.productservice.product.domain.model.ProductDailyView;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProductDailyViewCommandRepository {
  void upsert(UUID id, UUID productId, long viewCount, LocalDate viewDate);

  List<ProductDailyView> findAllByViewDate(LocalDate viewDate);
}
