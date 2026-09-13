package com.sub9.productservice.product.application.port.out.product;

import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// TODO : 추후 읽기 모델로 수정할 예정
public interface ProductQueryRepository {
  Optional<ProductDetailInfo> findProductDetailById(UUID productId);

  List<SkuInfo> getCartItemProducts(List<UUID> skuIds);

  Page<ProductInfo> searchProducts(String keyword, Set<UUID> metadataProductIds, Pageable pageable);

  List<ProductInfo> findProductsByIds(List<UUID> productIds);

  boolean existsSkuOwnedByCreatorId(UUID creatorId, UUID skuId);

  boolean existsById(UUID productId);
}
