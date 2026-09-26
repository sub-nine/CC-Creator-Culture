package com.sub9.orderservice.cart.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import com.sub9.orderservice.cart.application.dto.DeleteCartItemCommand;
import com.sub9.orderservice.cart.application.dto.UpdateCartItemCommand;
import com.sub9.orderservice.cart.application.port.out.CartProductPort;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.infrastructure.persistence.CartJpaRepository;
import com.sub9.orderservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Transactional
@SpringBootTest
@DisplayName("CartCommandService - 통합 테스트")
class CartCommandServiceIntegrationTest extends AbstractIntegrationTest {
  @MockitoBean private CartProductPort cartProductPort;
  @Autowired private CartCommandService cartCommandService;
  @Autowired private CartJpaRepository cartRepository;
  @Autowired private EntityManager entityManager;
  private UUID userId;
  private UUID skuId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    skuId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("상품 및 SKU 삭제에 따른 장바구니 정리 성공 테스트")
  class CleanupCartItemsTests {
    @Test
    @DisplayName("삭제된 상품의 모든 사용자 항목을 정리한다.")
    void cleanupByProductId_success() {
      // given
      UUID productId = UUID.randomUUID();
      Cart first = cartRepository.save(Cart.create(UUID.randomUUID(), userId, productId, skuId, 1));
      Cart second =
          cartRepository.save(
              Cart.create(UUID.randomUUID(), UUID.randomUUID(), productId, UUID.randomUUID(), 2));
      Cart other =
          cartRepository.save(
              Cart.create(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(), 1));
      entityManager.flush();
      entityManager.clear();

      // when
      cartCommandService.cleanupByProductId(productId);
      cartCommandService.cleanupByProductId(productId);
      entityManager.flush();
      entityManager.clear();

      // then
      assertThat(cartRepository.findById(first.getId())).isEmpty();
      assertThat(cartRepository.findById(second.getId())).isEmpty();
      assertThat(cartRepository.findById(other.getId())).isPresent();
    }

    @Test
    @DisplayName("삭제된 SKU의 모든 사용자 항목을 정리한다.")
    void cleanupBySkuId_success() {
      // given
      UUID productId = UUID.randomUUID();

      Cart first = cartRepository.save(Cart.create(UUID.randomUUID(), userId, productId, skuId, 1));
      Cart second =
          cartRepository.save(
              Cart.create(UUID.randomUUID(), UUID.randomUUID(), productId, skuId, 2));
      Cart otherSku =
          cartRepository.save(
              Cart.create(UUID.randomUUID(), userId, productId, UUID.randomUUID(), 3));
      Cart otherProduct =
          cartRepository.save(
              Cart.create(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(), 4));
      entityManager.flush();
      entityManager.clear();

      // when
      cartCommandService.cleanupBySkuId(skuId);
      cartCommandService.cleanupBySkuId(skuId);
      entityManager.flush();
      entityManager.clear();

      // then
      assertThat(cartRepository.findById(first.getId())).isEmpty();
      assertThat(cartRepository.findById(second.getId())).isEmpty();
      assertThat(cartRepository.findById(otherSku.getId())).isPresent();
      assertThat(cartRepository.findById(otherProduct.getId())).isPresent();
    }
  }

  @Nested
  @DisplayName("장바구니 등록 성공 테스트")
  class AddCartItemTests {
    @Test
    @DisplayName("상품 검증에 성공하면 사용자와 SKU, 수량을 DB에 저장한다.")
    void addCartItem_success() {
      // given
      AddCartItemCommand command = new AddCartItemCommand(userId, skuId, 2);

      UUID productId = UUID.randomUUID();
      org.mockito.BDDMockito.given(cartProductPort.getValidatedProductIdForCart(skuId))
          .willReturn(productId);

      // when
      UUID cartId = cartCommandService.addCartItem(command);
      entityManager.flush();
      entityManager.clear();

      // then
      Cart saved =
          cartRepository.findAll().stream()
              .filter(cart -> cart.getUserId().equals(userId))
              .findFirst()
              .orElseThrow();

      assertThat(saved.getProductId()).isEqualTo(productId);
      assertThat(saved.getId()).isEqualTo(cartId);
      assertThat(saved.getUserId()).isEqualTo(userId);
      assertThat(saved.getSkuId()).isEqualTo(skuId);
      assertThat(saved.getQuantity()).isEqualTo(2);
      assertThat(cartRepository.countByUserId(userId)).isEqualTo(1);
      verify(cartProductPort).getValidatedProductIdForCart(skuId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("장바구니를 등록하면 상품 검증은 트랜잭션 밖에서 호출하고 항목을 저장한다.")
    void when_cart_item_is_added_product_validation_is_called_outside_transaction() {
      // given
      List<Boolean> transactionActive = new ArrayList<>();
      org.mockito.BDDMockito.given(cartProductPort.getValidatedProductIdForCart(skuId))
          .willAnswer(
              invocation -> {
                transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
                return UUID.randomUUID();
              });

      // when
      UUID cartId = cartCommandService.addCartItem(new AddCartItemCommand(userId, skuId, 1));

      // then
      try {
        assertThat(transactionActive).containsExactly(false);
        assertThat(cartRepository.findById(cartId)).isPresent();
      } finally {
        cartRepository.deleteById(cartId);
      }
    }
  }

  @Nested
  @DisplayName("장바구니 수정 성공 테스트")
  class UpdateCartItemTests {
    @ParameterizedTest
    @ValueSource(ints = {1, 99})
    @DisplayName("상품 수량 변경에 성공한다.")
    void updateCartItem_success(int quantity) {
      // given
      Cart target =
          cartRepository.save(Cart.create(UUID.randomUUID(), userId, UUID.randomUUID(), skuId, 2));

      entityManager.flush();
      entityManager.clear();

      // when
      cartCommandService.updateCartItem(
          new UpdateCartItemCommand(userId, target.getId(), quantity));

      entityManager.flush();
      entityManager.clear();

      // then
      Cart updated = cartRepository.findById(target.getId()).orElseThrow();

      assertThat(updated.getQuantity()).isEqualTo(quantity);
      assertThat(updated.getUserId()).isEqualTo(userId);
      assertThat(updated.getSkuId()).isEqualTo(skuId);
    }
  }

  @Nested
  @DisplayName("장바구니 삭제 성공 테스트")
  class RemoveCartItemTests {
    @Test
    @DisplayName("선택한 본인 항목만 삭제하고 타인 항목과 선택하지 않은 항목은 유지한다.")
    void removeCartItem_success() {
      // given
      Cart first =
          cartRepository.save(Cart.create(UUID.randomUUID(), userId, UUID.randomUUID(), skuId, 2));
      Cart second =
          cartRepository.save(
              Cart.create(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(), 3));
      Cart unselected =
          cartRepository.save(
              Cart.create(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(), 1));
      Cart other =
          cartRepository.save(
              Cart.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), skuId, 4));

      DeleteCartItemCommand command =
          new DeleteCartItemCommand(
              userId, List.of(first.getId(), second.getId(), other.getId(), UUID.randomUUID()));

      entityManager.flush();
      entityManager.clear();

      // when
      cartCommandService.removeCartItem(command);

      entityManager.flush();
      entityManager.clear();

      // then
      assertThat(cartRepository.findById(first.getId())).isEmpty();
      assertThat(cartRepository.findById(second.getId())).isEmpty();
      assertThat(cartRepository.findById(unselected.getId())).isPresent();
      assertThat(cartRepository.findById(other.getId())).isPresent();
      assertThat(cartRepository.countByUserId(userId)).isEqualTo(1);
    }
  }
}
