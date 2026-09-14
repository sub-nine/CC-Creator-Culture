package com.sub9.productservice.product.application.port.in.product;

import com.sub9.productservice.product.application.query.dto.SkuInfo;
import java.util.List;
import java.util.UUID;

public interface CartProductQueryUseCase {
  List<SkuInfo> getCartItemProducts(List<UUID> skuIds);

  void validateSkuForCart(UUID skuId);
}
