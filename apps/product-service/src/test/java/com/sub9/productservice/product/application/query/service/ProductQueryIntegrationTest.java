package com.sub9.productservice.product.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.sku.SkuCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.stock.StockCommandJpaRepository;
import com.sub9.productservice.product.presentation.query.dto.ProductDetailResponse;
import com.sub9.productservice.product.presentation.query.dto.ProductResponse;
import com.sub9.productservice.product.presentation.query.dto.SkuResponse;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("ProductQueryService - 통합 테스트")
class ProductQueryIntegrationTest extends AbstractIntegrationTest {
  @Autowired ProductQueryService productQueryService;
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired SkuCommandJpaRepository skuRepository;
  @Autowired StockCommandJpaRepository stockRepository;
  @Autowired EntityManager entityManager;

  private final UUID creatorId = UUID.randomUUID();
  private Product dummyProduct;
  private Sku defaultSku;
  private Sku normalSku;

  @BeforeEach
  void setUp() {
    dummyProduct = productRepository.save(Product.create(creatorId, "말랑이", "말랑이 설명"));

    defaultSku = skuRepository.save(Sku.create(dummyProduct.getId(), "핑크", 10000L, true));
    normalSku = skuRepository.save(Sku.create(dummyProduct.getId(), "블루", 12000L, false));

    stockRepository.save(Stock.create(defaultSku.getId(), 10));
    stockRepository.save(Stock.create(normalSku.getId(), 5));

    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("상품 상세 조회에 성공하면 상품, SKU, 재고 정보를 반환한다.")
  void getProductDetail_success() {
    // when
    ProductDetailResponse response = productQueryService.getProductDetail(dummyProduct.getId(), null);

    // then
    assertThat(response.productId()).isEqualTo(dummyProduct.getId());
    assertThat(response.creatorId()).isEqualTo(creatorId);
    assertThat(response.name()).isEqualTo("말랑이");
    assertThat(response.content()).isEqualTo("말랑이 설명");
    assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(response.viewCount()).isZero();
    assertThat(response.averageRating()).isNull();
    assertThat(response.reviewCount()).isZero();
    assertThat(response.category()).isNull();
    assertThat(response.hashtags()).isEmpty();
    assertThat(response.skus()).hasSize(2);

    ProductDetailResponse.SkuInfo firstSku = response.skus().get(0);
    ProductDetailResponse.SkuInfo secondSku = response.skus().get(1);

    assertThat(firstSku.skuId()).isEqualTo(defaultSku.getId());
    assertThat(firstSku.name()).isEqualTo("핑크");
    assertThat(firstSku.price()).isEqualTo(10000L);
    assertThat(firstSku.isDefault()).isTrue();
    assertThat(firstSku.quantity()).isEqualTo(10);

    assertThat(secondSku.skuId()).isEqualTo(normalSku.getId());
    assertThat(secondSku.name()).isEqualTo("블루");
    assertThat(secondSku.price()).isEqualTo(12000L);
    assertThat(secondSku.isDefault()).isFalse();
    assertThat(secondSku.quantity()).isEqualTo(5);
  }

  @Test
  @DisplayName("SKU ID 목록으로 상품, SKU, 재고 정보 조회에 성공한다.")
  void getSkus_success() {
    // given
    List<UUID> skuIds = List.of(defaultSku.getId(), normalSku.getId());

    // when
    List<SkuResponse> responses = productQueryService.getSkus(skuIds);

    // then
    assertThat(responses).hasSize(2);

    SkuResponse defaultSkuResponse = findSkuResponse(responses, defaultSku.getId());
    SkuResponse normalSkuResponse = findSkuResponse(responses, normalSku.getId());

    assertThat(defaultSkuResponse.productId()).isEqualTo(dummyProduct.getId());
    assertThat(defaultSkuResponse.creatorId()).isEqualTo(creatorId);
    assertThat(defaultSkuResponse.productName()).isEqualTo("말랑이");
    assertThat(defaultSkuResponse.skuName()).isEqualTo("핑크");
    assertThat(defaultSkuResponse.productStatus()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(defaultSkuResponse.price()).isEqualTo(10000L);
    assertThat(defaultSkuResponse.quantity()).isEqualTo(10);

    assertThat(normalSkuResponse.productId()).isEqualTo(dummyProduct.getId());
    assertThat(normalSkuResponse.creatorId()).isEqualTo(creatorId);
    assertThat(normalSkuResponse.productName()).isEqualTo("말랑이");
    assertThat(normalSkuResponse.skuName()).isEqualTo("블루");
    assertThat(normalSkuResponse.productStatus()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(normalSkuResponse.price()).isEqualTo(12000L);
    assertThat(normalSkuResponse.quantity()).isEqualTo(5);
  }

  @Test
  @DisplayName("키워드로 상품 검색에 성공하면 대표 SKU와 재고 정보를 반환한다.")
  void searchProducts_success() {
    // given
    String keyword = "말랑";
    PageRequest pageable = PageRequest.of(0, 10);

    // when
    Page<ProductResponse> responses = productQueryService.searchProducts(keyword, pageable);

    // then
    assertThat(responses.getTotalElements()).isEqualTo(1);
    assertThat(responses.getContent()).hasSize(1);

    ProductResponse response = responses.getContent().getFirst();

    assertThat(response.productId()).isEqualTo(dummyProduct.getId());
    assertThat(response.name()).isEqualTo("말랑이");
    assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(response.averageRating()).isNull();
    assertThat(response.reviewCount()).isZero();
    assertThat(response.price()).isEqualTo(defaultSku.getPrice());
    assertThat(response.quantity()).isEqualTo(10);
  }

  private SkuResponse findSkuResponse(List<SkuResponse> responses, UUID skuId) {
    return responses.stream()
        .filter(response -> response.skuId().equals(skuId))
        .findFirst()
        .orElseThrow();
  }
}
