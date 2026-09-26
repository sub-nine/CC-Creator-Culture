package com.sub9.productservice.product.application.query.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.port.in.product.CartProductQueryUseCase;
import com.sub9.productservice.product.application.port.out.product.ProductQueryRepository;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.exception.SkuErrorCode;
import com.sub9.productservice.product.domain.model.ProductStatus;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartProductQueryService implements CartProductQueryUseCase {
  private final ProductQueryRepository productQueryRepository;

  @Override
  public List<SkuInfo> getCartItemProducts(List<UUID> skuIds) {
    if (skuIds.isEmpty()) {
      return List.of();
    }
    return productQueryRepository.getCartItemProducts(skuIds);
  }

  @Override
  public UUID getValidatedProductIdForCart(UUID skuId) {
    List<SkuInfo> skuinfos = productQueryRepository.getCartItemProducts(List.of(skuId));

    if (skuinfos.isEmpty()) throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
    SkuInfo skuInfo = skuinfos.getFirst();

    if (skuInfo.productStatus() != ProductStatus.ACTIVE)
      throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOR_SALE);
    if (skuInfo.quantity() <= 0) throw new BusinessException(SkuErrorCode.SKU_SOLD_OUT);

    return skuInfo.productId();
  }
}
