package com.sub9.productservice.product.application.port.out.product;

import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductQueryRepository {
  /**
   * 키워드와 메타데이터 검색 결과 기준으로 상품 목록 조회
   *
   * @return 상품 목록 데이터
   */
  Page<ProductInfo> searchProducts(String keyword, Set<UUID> metadataProductIds, Pageable pageable);

  /**
   * 삭제되지 않은 상품의 상세 정보 조회
   *
   * @return 상품의 상세 정보
   */
  Optional<ProductDetailInfo> findProductDetailById(UUID productId);

  /**
   * SKU 리스트 해당하는 판매 상품 정보 조회
   *
   * @return 조회 가능 SKU 상품 정보
   */
  List<SkuInfo> getCartItemProducts(List<UUID> skuIds);

  /**
   * 상품 ID 리스트에 해당하는 판매 상품 정보 조회
   *
   * @return 조회 가능한 상품 정보
   */
  List<ProductInfo> findProductsByIds(List<UUID> productIds);

  /**
   * 특정 창작자가 SKU를 소유하고 있는지 확인
   *
   * @param creatorId 창작자
   * @param skuId 조회할 SKU
   */
  boolean existsSkuOwnedByCreatorId(UUID creatorId, UUID skuId);

  /**
   * 상품이 존재하는지 확인
   *
   */
  boolean existsById(UUID productId);
}
