package com.sub9.productservice.product.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.entity.HashtagProduct;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.port.out.product.ProductUserPort;
import com.sub9.productservice.product.application.port.out.product.ProductViewRepository;
import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.product.application.query.dto.ProductViewCount;
import com.sub9.productservice.product.domain.model.Image;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductDailyViewCommandJPARepository;
import com.sub9.productservice.product.infrastructure.persistence.command.sku.SkuCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.stock.StockCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.scheduler.ProductTotalViewCountScheduler;
import com.sub9.productservice.product.infrastructure.scheduler.ProductViewCountScheduler;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
  @Autowired ProductViewRepository productViewRepository;
  @Autowired ProductDailyViewCommandJPARepository dailyViewRepository;

  @MockitoBean ProductViewCountScheduler productViewCountScheduler;
  @MockitoBean ProductTotalViewCountScheduler productTotalViewCountScheduler;
  @MockitoBean KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  @MockitoBean ProductUserPort productUserPort;

  private final UUID creatorId = UUID.randomUUID();
  private Product dummyProduct;
  private Sku defaultSku;
  private Sku normalSku;

  @BeforeEach
  void setUp() {
    productViewRepository.deleteAllViewCount();
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(creatorId, "CREATOR"));

    dummyProduct = productRepository.save(Product.create(creatorId, "말랑이", "말랑이 설명"));

    defaultSku = skuRepository.save(Sku.create(dummyProduct.getId(), "핑크", 10000L, true));
    normalSku = skuRepository.save(Sku.create(dummyProduct.getId(), "블루", 12000L, false));

    stockRepository.save(Stock.create(defaultSku.getId(), 10));
    stockRepository.save(Stock.create(normalSku.getId(), 5));

    entityManager.flush();
    entityManager.clear();
    given(productUserPort.getCreatorNamesByIds(List.of(creatorId)))
        .willReturn(Map.of(creatorId, "상호"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    productViewRepository.deleteAllViewCount();
  }

  @Test
  @DisplayName("상품 상세 조회에 성공하면 상품, 상호명, 조회수, SKU, 재고 정보를 반환한다.")
  void getProductDetail_success() {
    dailyViewRepository.upsert(
        UUID.randomUUID(), dummyProduct.getId(), 7L, LocalDate.now(Clock.systemUTC()));

    // when
    ProductDetailInfo response = productQueryService.getProductDetail(dummyProduct.getId(), null);

    // then
    assertThat(productViewRepository.findAllViewCounts()).isEmpty();
    assertThat(response.productId()).isEqualTo(dummyProduct.getId());
    assertThat(response.creatorId()).isEqualTo(creatorId);
    assertThat(response.creatorName()).isEqualTo("상호");
    assertThat(response.name()).isEqualTo("말랑이");
    assertThat(response.content()).isEqualTo("말랑이 설명");
    assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(response.viewCount()).isEqualTo(7L);
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
  @DisplayName("상품 검색에 성공하면 대표 SKU와 재고 정보를 반환한다.")
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
    assertThat(response.creatorName()).isEqualTo("상호");
    assertThat(response.name()).isEqualTo("말랑이");
    assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(response.averageRating()).isNull();
    assertThat(response.reviewCount()).isZero();
    assertThat(response.price()).isEqualTo(defaultSku.getPrice());
    assertThat(response.quantity()).isEqualTo(10);
    assertThat(response.imageKey()).isNull();
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

  @ParameterizedTest
  @ValueSource(strings = {"user:", "guest:"})
  @DisplayName("상품 상세 조히 시 조회수를 기록하고 같은 방문자의 재조회는 중복 집계하지 않는다.")
  void getProductDetail_success_when_same_visitor_returns(String prefix) {
    // given
    String visitorId = prefix + UUID.randomUUID();

    // when
    ProductDetailInfo response =
        productQueryService.getProductDetail(dummyProduct.getId(), visitorId);
    productQueryService.getProductDetail(dummyProduct.getId(), visitorId);

    // then
    assertThat(response.productId()).isEqualTo(dummyProduct.getId());
    assertThat(productViewRepository.findAllViewCounts())
        .containsExactly(new ProductViewCount(dummyProduct.getId(), 1L));
  }
}
