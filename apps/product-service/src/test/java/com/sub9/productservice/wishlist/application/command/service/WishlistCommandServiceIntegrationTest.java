package com.sub9.productservice.wishlist.application.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import com.sub9.productservice.wishlist.application.command.dto.AddToWishlistCommand;
import com.sub9.productservice.wishlist.application.command.dto.RemoveFromWishlistCommand;
import com.sub9.productservice.wishlist.domain.exception.WishlistErrorCode;
import com.sub9.productservice.wishlist.domain.model.Wishlist;
import com.sub9.productservice.wishlist.infrastructure.persistence.command.WishlistJpaRepository;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("WishlistCommandService - 통합 테스트")
class WishlistCommandServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired WishlistCommandService wishlistCommandService;
  @Autowired WishlistJpaRepository wishlistRepository;
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired EntityManager entityManager;

  private final UUID userId = UUID.randomUUID();
  private final UUID otherUserId = UUID.randomUUID();
  private final UUID creatorId = UUID.randomUUID();
  private Product product;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(creatorId, "CREATOR"));
    product = productRepository.save(Product.create(creatorId, "말랑이", "말랑이 설명"));
    flushAndClear();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("관심상품 등록 테스트")
  class AddTests {
    @Test
    @DisplayName("관심상품 등록에 성공하면 상품과 등록 시각이 저장된다.")
    void addToWishlist_success() {
      // given
      AddToWishlistCommand command = new AddToWishlistCommand(userId, product.getId());

      // when
      wishlistCommandService.addToWishlist(command);
      flushAndClear();

      // then
      assertThat(wishlistRepository.findAll())
          .singleElement()
          .satisfies(
              wishlist -> {
                assertThat(wishlist.getId()).isNotNull();
                assertThat(wishlist.getUserId()).isEqualTo(userId);
                assertThat(wishlist.getProductId()).isEqualTo(product.getId());
                assertThat(wishlist.getCreatedAt()).isNotNull();
              });
    }

    @Test
    @DisplayName("동일한 관심상품을 다시 등록해도 중복 저장하지 않는다.")
    void addToWishlist_success_when_already_registered() {
      // given
      Wishlist original = wishlistRepository.save(Wishlist.create(userId, product.getId()));
      flushAndClear();
      Wishlist saved = wishlistRepository.findById(original.getId()).orElseThrow();
      AddToWishlistCommand command = new AddToWishlistCommand(userId, product.getId());

      // when
      wishlistCommandService.addToWishlist(command);
      flushAndClear();

      // then
      assertThat(wishlistRepository.findAll())
          .singleElement()
          .satisfies(
              wishlist -> {
                assertThat(wishlist.getId()).isEqualTo(saved.getId());
                assertThat(wishlist.getCreatedAt()).isEqualTo(saved.getCreatedAt());
              });
    }
  }

  @Nested
  @DisplayName("관심상품 삭제 테스트")
  class RemoveTests {
    @Test
    @DisplayName("다른 사용자의 관심상품 삭제를 시도하면 WISHLIST_NOT_FOUND 예외가 발생해야 한다.")
    void removeFromWishlist_fails_when_wishlist_belongs_to_other_user() {
      Wishlist other = wishlistRepository.save(Wishlist.create(otherUserId, product.getId()));
      flushAndClear();

      assertThatThrownBy(
              () ->
                  wishlistCommandService.removeFromWishlist(
                      new RemoveFromWishlistCommand(userId, Set.of(other.getId()))))
          .isInstanceOf(BusinessException.class)
          .hasMessage(WishlistErrorCode.WISHLIST_NOT_FOUND.message());
      assertThat(wishlistRepository.findById(other.getId())).isPresent();
    }

    @Test
    @DisplayName("선택한 관심상품만 삭제한다.")
    void removeFromWishlist_success_when_multiple_products_are_selected() {
      // given
      Product second = productRepository.save(Product.create(creatorId, "두 번째 상품", "설명"));
      Product unselected = productRepository.save(Product.create(creatorId, "미선택 상품", "설명"));

      Wishlist firstWishlist = wishlistRepository.save(Wishlist.create(userId, product.getId()));
      Wishlist secondWishlist = wishlistRepository.save(Wishlist.create(userId, second.getId()));
      Wishlist remaining = wishlistRepository.save(Wishlist.create(userId, unselected.getId()));
      Wishlist other = wishlistRepository.save(Wishlist.create(otherUserId, second.getId()));

      flushAndClear();

      // when
      wishlistCommandService.removeFromWishlist(
          new RemoveFromWishlistCommand(
              userId, Set.of(firstWishlist.getId(), secondWishlist.getId(), other.getId())));
      flushAndClear();

      // then
      assertThat(wishlistRepository.findAll())
          .extracting(Wishlist::getId)
          .containsExactlyInAnyOrder(remaining.getId(), other.getId());
      assertThat(productRepository.findById(product.getId())).isPresent();
      assertThat(productRepository.findById(second.getId())).isPresent();
    }

    @Test
    @DisplayName("선택한 관심상품 일부가 없어도 존재하는 관심상품을 삭제한다.")
    void removeFromWishlist_success_when_some_wishlists_are_missing() {
      // given
      Wishlist mine = wishlistRepository.save(Wishlist.create(userId, product.getId()));
      flushAndClear();

      // when
      wishlistCommandService.removeFromWishlist(
          new RemoveFromWishlistCommand(userId, Set.of(mine.getId(), UUID.randomUUID())));
      flushAndClear();

      // then
      assertThat(wishlistRepository.findAll()).isEmpty();
      assertThat(productRepository.findById(product.getId())).isPresent();
    }

    @Test
    @DisplayName("상품별 정리 시 모든 사용자의 해당 관심상품을 삭제한다.")
    void cleanUpWByProductId_success_when_repeated() {
      // given
      wishlistRepository.save(Wishlist.create(userId, product.getId()));
      wishlistRepository.save(Wishlist.create(otherUserId, product.getId()));
      Product otherProduct = productRepository.save(Product.create(creatorId, "다른 상품", "설명"));
      Wishlist remaining = wishlistRepository.save(Wishlist.create(userId, otherProduct.getId()));
      flushAndClear();

      // when
      wishlistCommandService.cleanUpWByProductId(product.getId());
      flushAndClear();
      wishlistCommandService.cleanUpWByProductId(product.getId());
      flushAndClear();

      // then
      assertThat(wishlistRepository.findAll())
          .extracting(Wishlist::getId)
          .containsExactly(remaining.getId());
    }
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
