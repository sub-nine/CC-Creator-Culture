package com.sub9.productservice.product.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.entity.HashtagProduct;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import com.sub9.productservice.product.domain.model.Image;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.sku.SkuCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.stock.StockCommandJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
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
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(creatorId, "CREATOR"));

    dummyProduct = productRepository.save(Product.create(creatorId, "말랑이", "말랑이 설명"));

    defaultSku = skuRepository.save(Sku.create(dummyProduct.getId(), "핑크", 10000L, true));
    normalSku = skuRepository.save(Sku.create(dummyProduct.getId(), "블루", 12000L, false));

    stockRepository.save(Stock.create(defaultSku.getId(), 10));
    stockRepository.save(Stock.create(normalSku.getId(), 5));

    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("상품 상세 조회에 성공하면 상품, SKU, 재고 정보를 반환한다.")
  void getProductDetail_success() {
    // when
    ProductDetailInfo response = productQueryService.getProductDetail(dummyProduct.getId(), null);

    // then
    assertThat(response.productId()).isEqualTo(dummyProduct.getId());
    assertThat(response.creatorId()).isEqualTo(creatorId);
    assertThat(response.name()).isEqualTo("말랑이");
    assertThat(response.content()).isEqualTo("말랑이 설명");
    assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(response.viewCount()).isZero();
    assertThat(response.averageRating()).isNull();
    assertThat(response.reviewCount()).isZero();
    assertThat(response.categories()).isEmpty();
    assertThat(response.hashtags()).isEmpty();
    assertThat(response.skus()).hasSize(2);
    assertThat(response.images()).isEmpty();

    ProductDetailInfo.SkuInfo firstSku = response.skus().get(0);
    ProductDetailInfo.SkuInfo secondSku = response.skus().get(1);

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
  void getCartItemProducts_success() {
    // given
    List<UUID> skuIds = List.of(defaultSku.getId(), normalSku.getId());

    // when
    List<SkuInfo> responses = productQueryService.getCartItemProducts(skuIds);

    // then
    assertThat(responses).hasSize(2);

    SkuInfo defaultSkuInfo = findSkuResponse(responses, defaultSku.getId());
    SkuInfo normalSkuInfo = findSkuResponse(responses, normalSku.getId());

    assertThat(defaultSkuInfo.productId()).isEqualTo(dummyProduct.getId());
    assertThat(defaultSkuInfo.creatorId()).isEqualTo(creatorId);
    assertThat(defaultSkuInfo.productName()).isEqualTo("말랑이");
    assertThat(defaultSkuInfo.skuName()).isEqualTo("핑크");
    assertThat(defaultSkuInfo.productStatus()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(defaultSkuInfo.price()).isEqualTo(10000L);
    assertThat(defaultSkuInfo.quantity()).isEqualTo(10);

    assertThat(normalSkuInfo.productId()).isEqualTo(dummyProduct.getId());
    assertThat(normalSkuInfo.creatorId()).isEqualTo(creatorId);
    assertThat(normalSkuInfo.productName()).isEqualTo("말랑이");
    assertThat(normalSkuInfo.skuName()).isEqualTo("블루");
    assertThat(normalSkuInfo.productStatus()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(normalSkuInfo.price()).isEqualTo(12000L);
    assertThat(normalSkuInfo.quantity()).isEqualTo(5);
  }

  @Test
  @DisplayName("키워드로 상품 검색에 성공하면 대표 SKU와 재고 정보를 반환한다.")
  void searchProducts_success() {
    // given
    String keyword = "말랑";
    PageRequest pageable = PageRequest.of(0, 10);

    // when
    Page<ProductInfo> responses = productQueryService.searchProducts(keyword, pageable);

    // then
    assertThat(responses.getTotalElements()).isEqualTo(1);
    assertThat(responses.getContent()).hasSize(1);

    ProductInfo response = responses.getContent().getFirst();

    assertThat(response.productId()).isEqualTo(dummyProduct.getId());
    assertThat(response.name()).isEqualTo("말랑이");
    assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(response.averageRating()).isNull();
    assertThat(response.reviewCount()).isZero();
    assertThat(response.price()).isEqualTo(defaultSku.getPrice());
    assertThat(response.quantity()).isEqualTo(10);
    assertThat(response.imageKey()).isNull();
  }

  @Test
  @DisplayName("판매 중이고 재고가 남아있는 SKU는 장바구니에 등록할 수 있다.")
  void validateSkuForCart_success_when_active_product_has_stock() {
    // given
    entityManager
        .createQuery("UPDATE Stock s SET s.quantity = 1 WHERE s.skuId = :skuId")
        .setParameter("skuId", normalSku.getId())
        .executeUpdate();
    entityManager.clear();

    // when & then
    assertThatCode(() -> productQueryService.validateSkuForCart(normalSku.getId()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("카테고리와 해시태그 이름으로 상품 검색에 성공하면 전체 건수를 반환한다.")
  void searchProducts_success_when_metadata_matches() {
    // given
    Category category = Category.create("여름 의류", "설명");
    Hashtag hashtag = Hashtag.create("여름 추천");

    entityManager.persist(category);
    entityManager.persist(hashtag);
    entityManager.persist(
        CategoryHashtag.create(
            category, hashtag, CategoryHashtagMatchType.MANUAL, CategoryHashtagStatus.MERGED, 0.0));
    entityManager.persist(HashtagProduct.create(hashtag, dummyProduct.getId()));

    Product otherProduct = productRepository.save(Product.create(creatorId, "티셔츠", "설명"));
    Sku otherSku = skuRepository.save(Sku.create(otherProduct.getId(), "화이트", 15000L, true));
    stockRepository.save(Stock.create(otherSku.getId(), 3));

    entityManager.persist(HashtagProduct.create(hashtag, otherProduct.getId()));
    entityManager.flush();
    entityManager.clear();

    // when
    Page<ProductInfo> categoryResponses =
        productQueryService.searchProducts("의류", PageRequest.of(0, 1));
    Page<ProductInfo> hashtagResponses =
        productQueryService.searchProducts("추천", PageRequest.of(0, 1));
    Page<ProductInfo> responses = productQueryService.searchProducts("여름", PageRequest.of(0, 1));
    Page<ProductInfo> nextResponses =
        productQueryService.searchProducts("여름", PageRequest.of(1, 1));

    // then
    // dummyProduct와 otherProduct 모두 "여름 추천" 해시태그를 공유하고, 그 해시태그가 "여름 의류" 카테고리에 MERGED돼있으므로
    // 카테고리 키워드 검색에도 둘 다 매칭된다
    assertThat(categoryResponses.getTotalElements()).isEqualTo(2);
    assertThat(hashtagResponses.getTotalElements()).isEqualTo(2);
    assertThat(responses.getTotalElements()).isEqualTo(2);
    assertThat(nextResponses.getTotalElements()).isEqualTo(2);
    assertThat(responses.getContent()).hasSize(1);
    assertThat(nextResponses.getContent()).hasSize(1);
    assertThat(responses.getContent().getFirst().productId())
        .isNotEqualTo(nextResponses.getContent().getFirst().productId());
  }

  @Test
  @DisplayName("상품 상세 조회에 성공하면 카테고리, 해시태그, 정렬된 이미지 정보를 반환한다.")
  void getProductDetail_success_with_metadata_and_images() {
    // given
    Category category = Category.create("의류", "설명");
    Hashtag hashtag = Hashtag.create("여름");
    entityManager.persist(category);
    entityManager.persist(hashtag);
    entityManager.persist(
        CategoryHashtag.create(
            category, hashtag, CategoryHashtagMatchType.MANUAL, CategoryHashtagStatus.MERGED, 0.0));
    entityManager.persist(HashtagProduct.create(hashtag, dummyProduct.getId()));

    Image originalImage = Image.create(dummyProduct.getId(), "original/detail.png", null, 1);
    Image processedImage =
        Image.create(dummyProduct.getId(), "original/main.png", "processed/main.webp", 0);
    Image deletedImage = Image.create(dummyProduct.getId(), "original/deleted.png", null, 2);

    entityManager.persist(originalImage);
    entityManager.persist(processedImage);
    entityManager.persist(deletedImage);
    entityManager.flush();
    entityManager.remove(deletedImage);
    entityManager.flush();
    entityManager.clear();

    // when
    ProductDetailInfo response = productQueryService.getProductDetail(dummyProduct.getId(), null);
    Page<ProductInfo> responses = productQueryService.searchProducts("말랑", PageRequest.of(0, 10));

    // then
    assertThat(response.categories())
        .containsExactly(new ProductDetailInfo.CategoryInfo(category.getId(), "의류"));
    assertThat(response.hashtags())
        .containsExactly(new ProductDetailInfo.HashtagInfo(hashtag.getId(), "여름"));
    assertThat(response.images())
        .containsExactly(
            new ProductDetailInfo.ImageInfo(processedImage.getId(), "processed/main.webp", 0),
            new ProductDetailInfo.ImageInfo(originalImage.getId(), "original/detail.png", 1));
    assertThat(responses.getContent().getFirst().imageKey()).isEqualTo("processed/main.webp");
  }

  private SkuInfo findSkuResponse(List<SkuInfo> responses, UUID skuId) {
    return responses.stream()
        .filter(response -> response.skuId().equals(skuId))
        .findFirst()
        .orElseThrow();
  }
}
