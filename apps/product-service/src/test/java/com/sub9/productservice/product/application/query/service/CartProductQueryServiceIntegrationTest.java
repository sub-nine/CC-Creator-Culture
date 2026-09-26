package com.sub9.productservice.product.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import com.sub9.productservice.product.domain.model.*;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("CartProductQueryService - 통합 테스트")
class CartProductQueryServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired CartProductQueryService cartProductQueryService;
  @Autowired EntityManager entityManager;

  private final UUID creatorId = UUID.randomUUID();
  private Product dummyProduct;
  private Sku defaultSku;
  private Sku normalSku;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(creatorId, "CREATOR"));
    dummyProduct = Product.create(creatorId, "말랑이", "말랑이 설명");

    entityManager.persist(dummyProduct);

    defaultSku = Sku.create(dummyProduct.getId(), "핑크", 10000L, true);
    normalSku = Sku.create(dummyProduct.getId(), "블루", 12000L, false);

    entityManager.persist(defaultSku);
    entityManager.persist(normalSku);
    entityManager.persist(Stock.create(defaultSku.getId(), 10));
    entityManager.persist(Stock.create(normalSku.getId(), 5));
    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("SKU ID 목록으로 상품, SKU, 재고 정보 조회에 성공한다.")
  void getCartItemProducts_success() {
    // given
    List<UUID> skuIds = List.of(defaultSku.getId(), normalSku.getId());

    // when
    List<SkuInfo> responses = cartProductQueryService.getCartItemProducts(skuIds);

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
  @DisplayName("판매 중이고 재고가 남아있는 SKU는 상품 ID를 반환한다.")
  void getValidatedProductIdForCart_success() {
    // given
    UUID skuId = normalSku.getId();

    // when
    UUID productId = cartProductQueryService.getValidatedProductIdForCart(skuId);

    // then
    assertThat(productId).isEqualTo(dummyProduct.getId());
  }

  @Test
  @DisplayName("빈 SKU 목록을 조회하면 빈 목록을 반환한다.")
  void getCartItemProducts_success_when_empty() {
    // given
    List<UUID> skuIds = List.of();

    // when
    List<SkuInfo> response = cartProductQueryService.getCartItemProducts(skuIds);

    // then
    assertThat(response).isEmpty();
  }

  @Test
  @DisplayName("삭제된 상품과 SKU는 장바구니 상품 조회에서 제외한다.")
  void getCartItemProducts_success_when_deleted() {
    // given
    entityManager
        .createQuery("UPDATE Sku s SET s.deletedAt = CURRENT_TIMESTAMP WHERE s.id = :id")
        .setParameter("id", normalSku.getId())
        .executeUpdate();
    entityManager.clear();

    // when
    List<SkuInfo> response =
        cartProductQueryService.getCartItemProducts(List.of(defaultSku.getId(), normalSku.getId()));

    // then
    assertThat(response).extracting(SkuInfo::skuId).containsExactly(defaultSku.getId());

    // given
    entityManager
        .createQuery("UPDATE Product p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
        .setParameter("id", dummyProduct.getId())
        .executeUpdate();
    entityManager.clear();

    // when
    List<SkuInfo> deletedResponse =
        cartProductQueryService.getCartItemProducts(List.of(defaultSku.getId()));

    // then
    assertThat(deletedResponse).isEmpty();
  }

  private SkuInfo findSkuResponse(List<SkuInfo> responses, UUID skuId) {
    return responses.stream()
        .filter(response -> response.skuId().equals(skuId))
        .findFirst()
        .orElseThrow();
  }
}
