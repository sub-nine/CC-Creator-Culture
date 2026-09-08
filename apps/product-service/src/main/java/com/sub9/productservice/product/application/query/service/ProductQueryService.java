package com.sub9.productservice.product.application.query.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.event.ProductViewedEvent;
import com.sub9.productservice.product.application.query.repository.ProductQueryRepository;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import java.util.List;
import java.util.UUID;

import com.sub9.productservice.product.domain.model.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {
  private final ProductQueryRepository productQueryRepository;
  private final ApplicationEventPublisher eventPublisher;

  public Page<ProductInfo> searchProducts(String keyword, Pageable pageable) {
    // TODO : 현재 정지 상태인 상품을 모두 조회를 할 수 있는 문제가 있어 추후 수정예정
    return productQueryRepository.searchProducts(keyword, pageable);
  }

  public ProductDetailInfo getProductDetail(UUID productId, UUID visitorId) {
    // TODO : 현재 정지 상태인 상품을 모두 조회를 할 수 있는 문제가 있어 추후 수정예정
    ProductDetailInfo response =  productQueryRepository
            .findProductDetailById(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    // TODO : MVP 단계에선 로그인 유저만 조회수를 증가시키도록 구현한다.
    if (visitorId != null) eventPublisher.publishEvent(new ProductViewedEvent(productId, visitorId));
    return response;
  }

  public List<SkuInfo> getCartItemProducts(List<UUID> skuIds) {
    if (skuIds.isEmpty()) {
      return List.of();
    }
    return productQueryRepository.getCartItemProducts(skuIds);
  }

  public void validateSkuForCart(UUID skuId) {
    List<SkuInfo> skuinfos = productQueryRepository.getCartItemProducts(List.of(skuId));

    if (skuinfos.isEmpty()) throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
    SkuInfo skuInfo = skuinfos.getFirst();

    if (skuInfo.productStatus() != ProductStatus.ACTIVE) throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOR_SALE);
    if (skuInfo.quantity() <= 0) throw new  BusinessException(ProductErrorCode.SKU_SOLD_OUT);
  }
}
