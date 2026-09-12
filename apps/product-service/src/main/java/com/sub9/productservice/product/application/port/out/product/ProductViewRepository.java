package com.sub9.productservice.product.application.port;

import com.sub9.productservice.product.application.query.dto.ProductViewCount;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface ProductViewRepository {
  boolean recordView(UUID productId, UUID viewerId, Duration ttl);

  List<ProductViewCount> findAllViewCounts();

  void deleteAllViewCount();
}
