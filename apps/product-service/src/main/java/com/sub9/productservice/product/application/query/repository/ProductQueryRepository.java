package com.sub9.productservice.product.application.query.repository;

import com.sub9.productservice.product.presentation.query.dto.ProductDetailResponse;
import com.sub9.productservice.product.presentation.query.dto.ProductResponse;
import com.sub9.productservice.product.presentation.query.dto.SkuResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductQueryRepository {
  Optional<ProductDetailResponse> findProductDetailById(UUID productId);

  List<SkuResponse> findAllSkuInfoByIds(List<UUID> skuIds);

  Page<ProductResponse> searchProducts(String keyword, Pageable pageable);
}
