package com.sub9.productservice.product.application.query.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.event.ProductViewedEvent;
import com.sub9.productservice.product.application.port.in.product.CartProductQueryUseCase;
import com.sub9.productservice.product.application.port.in.product.ProductQueryUseCase;
import com.sub9.productservice.product.application.port.out.product.ProductMetadataQueryPort;
import com.sub9.productservice.product.application.port.out.product.ProductQueryRepository;
import com.sub9.productservice.product.application.port.out.product.ProductUserPort;
import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.exception.SkuErrorCode;
import com.sub9.productservice.product.domain.model.ProductStatus;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService implements ProductQueryUseCase, CartProductQueryUseCase {
  private final ProductQueryRepository productQueryRepository;
  private final ProductMetadataQueryPort metadataQueryPort;
  private final ApplicationEventPublisher eventPublisher;
  private final ProductUserPort productUserPort;

  // TODO : 현재 조회 로직에 정지 상태인 상품을 모두 조회를 할 수 있는 문제가 있어 추후 수정예정
  //        창작자 (본인 상품만), 관리자(정지 상품 전체)
  // TODO : 리뷰의 경우 작성 시 Product에 반영하는 로직이 누락된 상태, 추후 해당 기능 추가 예정
  @Override
  public Page<ProductInfo> searchProducts(String keyword, Pageable pageable) {
    Set<UUID> metadataProductIds =
        metadataQueryPort.findProductIdsByMetadataKeyword(keyword, 100000);

    Page<ProductInfo> products =
        productQueryRepository.searchProducts(keyword, metadataProductIds, pageable);

    List<UUID> creatorIds =
        products.getContent().stream()
            .map(ProductInfo::creatorId)
            .distinct()
            .toList();

    Map<UUID, String> creatorNames =
        creatorIds.isEmpty() ? Map.of() : productUserPort.getCreatorNamesByIds(creatorIds);

    return products.map(product -> ProductInfo.of(product, creatorNames.get(product.creatorId())));
  }

  @Override
  public ProductDetailInfo getProductDetail(UUID productId, String visitorId) {
    ProductDetailInfo info =
        productQueryRepository
            .findProductDetailById(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    var metadata = metadataQueryPort.getProductMetadata(productId);

    Map<UUID, String> creatorNames =
        productUserPort.getCreatorNamesByIds(List.of(info.creatorId()));

    if (visitorId != null) {
      eventPublisher.publishEvent(new ProductViewedEvent(productId, visitorId));
    }

    return info.withDetails(metadata, creatorNames.get(info.creatorId()));
  }

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
