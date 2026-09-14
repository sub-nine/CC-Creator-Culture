package com.sub9.productservice.wishlist.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.domain.model.Image;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.support.AbstractIntegrationTest;
import com.sub9.productservice.wishlist.application.query.dto.WishlistInfo;
import com.sub9.productservice.wishlist.domain.model.Wishlist;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("WishlistQueryService - 통합 테스트")
class WishlistQueryServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired WishlistQueryService wishlistQueryService;
  @Autowired EntityManager entityManager;

  private final UUID userId = UUID.randomUUID();
  private final UUID creatorId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(creatorId, "CREATOR"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @CsvSource({"0, 0, false", "10, 10, false", "11, 10, true"})
  @DisplayName("관심상품 개수에 따라 첫 페이지의 크기와 다음 페이지 여부를 반환한다.")
  void getWishlist_success_when_page_boundary(int count, int expectedSize, boolean hasNext) {
    // given
    List<Wishlist> wishlists = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      wishlists.add(saveWishlist(userId, saveProduct("상품" + i), i));
    }
    flushAndClear();

    // when
    Slice<WishlistInfo> response = wishlistQueryService.getWishlist(userId, PageRequest.of(0, 10));

    // then
    assertThat(response.getContent()).hasSize(expectedSize);
    assertThat(response.hasNext()).isEqualTo(hasNext);
    assertThat(response.getNumber()).isZero();
    List<UUID> expectedIds = wishlists.reversed().stream().limit(10).map(Wishlist::getId).toList();
    assertThat(response.getContent())
        .extracting(WishlistInfo::wishlistId)
        .containsExactlyElementsOf(expectedIds);
  }

  @Test
  @DisplayName("다음 페이지는 이전 페이지와 중복 없이 남은 관심상품을 반환한다.")
  void getWishlist_success_when_next_page() {
    // given
    List<Wishlist> wishlists = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      wishlists.add(saveWishlist(userId, saveProduct("상품" + i), i));
    }
    flushAndClear();

    // when
    Slice<WishlistInfo> response = wishlistQueryService.getWishlist(userId, PageRequest.of(1, 10));

    // then
    assertThat(response.getContent())
        .extracting(WishlistInfo::wishlistId)
        .containsExactly(wishlists.getFirst().getId());
    assertThat(response.hasNext()).isFalse();
    assertThat(response.getNumber()).isEqualTo(1);
  }

  @Test
  @DisplayName("내 관심상품만 조회하고 대표 SKU 가격과 가공된 대표 이미지를 반환한다.")
  void getWishlist_success_with_product_information() {
    // given
    Product product = saveProduct("말랑이");

    entityManager.persist(Sku.create(product.getId(), "일반 옵션", 500L, false));
    entityManager.persist(
        Image.create(product.getId(), "original/main.png", "processed/main.webp", 0));
    entityManager.persist(Image.create(product.getId(), "original/detail.png", null, 1));

    Wishlist wishlist = saveWishlist(userId, product, 0);
    saveWishlist(UUID.randomUUID(), saveProduct("다른 사용자 상품"), 1);
    flushAndClear();

    // when
    Slice<WishlistInfo> response = wishlistQueryService.getWishlist(userId, PageRequest.of(0, 10));

    // then
    assertThat(response.getContent())
        .containsExactly(
            new WishlistInfo(
                wishlist.getId(), product.getId(), "말랑이", "ACTIVE", 10000L, "processed/main.webp"));
    assertThat(response.hasNext()).isFalse();
  }

  @Test
  @DisplayName("삭제된 상품은 제외하고 원래 관심상품 기준의 다음 페이지 여부를 유지한다.")
  void getWishlist_success_when_deleted_product_is_pending_cleanup() {
    // given
    for (int i = 0; i < 11; i++) {
      Product product = saveProduct("상품" + i);
      saveWishlist(userId, product, i);
      if (i == 10) {
        entityManager.flush();
        entityManager
            .createQuery("UPDATE Product p SET p.deletedAt = :deletedAt WHERE p.id = :id")
            .setParameter("deletedAt", Instant.now())
            .setParameter("id", product.getId())
            .executeUpdate();
      }
    }
    flushAndClear();

    // when
    Slice<WishlistInfo> response = wishlistQueryService.getWishlist(userId, PageRequest.of(0, 10));

    // then
    assertThat(response.getContent()).hasSize(9);
    assertThat(response.getContent()).extracting(WishlistInfo::productName).doesNotContain("상품10");
    assertThat(response.hasNext()).isTrue();
  }

  private Product saveProduct(String name) {
    Product product = Product.create(creatorId, name, "상품 설명");
    entityManager.persist(product);
    Sku sku = Sku.create(product.getId(), "대표 옵션", 10000L, true);
    entityManager.persist(sku);
    entityManager.persist(Stock.create(sku.getId(), 0));
    return product;
  }

  private Wishlist saveWishlist(UUID ownerId, Product product, int order) {
    Wishlist wishlist = Wishlist.create(ownerId, product.getId());
    entityManager.persist(wishlist);
    entityManager.flush();
    entityManager
        .createQuery("UPDATE Wishlist w SET w.createdAt = :createdAt WHERE w.id = :id")
        .setParameter("createdAt", Instant.parse("2026-09-13T00:00:00Z").plusSeconds(order))
        .setParameter("id", wishlist.getId())
        .executeUpdate();
    return wishlist;
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
