package com.sub9.productservice.product.application.port.in.product;

import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductQueryUseCase {
  Page<ProductInfo> searchProducts(String keyword, Pageable pageable);

  ProductDetailInfo getProductDetail(UUID productId, UUID visitorId);
}
