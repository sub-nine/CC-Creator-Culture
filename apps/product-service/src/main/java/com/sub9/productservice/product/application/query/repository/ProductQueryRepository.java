package com.sub9.productservice.product.application.query.repository;

import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductQueryRepository {
  Optional<ProductDetailInfo> findProductDetailById(UUID productId);

  List<SkuInfo> findAllSkuInfoByIds(List<UUID> skuIds);

  Page<ProductInfo> searchProducts(String keyword, Pageable pageable);
}
