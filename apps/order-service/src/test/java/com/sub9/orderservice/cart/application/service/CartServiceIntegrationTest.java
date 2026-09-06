package com.sub9.orderservice.cart.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import com.sub9.orderservice.cart.application.dto.DeleteCartItemCommand;
import com.sub9.orderservice.cart.application.dto.UpdateCartItemCommand;
import com.sub9.orderservice.cart.application.port.CartProductPort;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.infrastructure.persistence.CartJpaRepository;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("CartService - 통합 테스트")
class CartServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired private CartService cartService;
  @Autowired private CartJpaRepository cartRepository;
  @Autowired private EntityManager entityManager;
  @MockitoBean private CartProductPort cartProductPort;
  @MockitoBean private CouponApplicationPort couponApplicationPort;
  @MockitoBean private CouponUsagePort couponUsagePort;
  @MockitoBean private StockPort stockPort;

  private UUID userId;
  private UUID skuId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    skuId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("장바구니 등록 성공 테스트")
  class AddCartItemTests {
    @Test
    @DisplayName("상품 검증에 성공하면 사용자와 SKU, 수량을 DB에 저장한다.")
    void addCartItem_success() {
      // given
      AddCartItemCommand command = new AddCartItemCommand(userId, skuId, 2);

      // when
      cartService.addCartItem(command);
      entityManager.flush();
      entityManager.clear();

      // then
      Cart saved =
          cartRepository.findAll().stream()
              .filter(cart -> cart.getUserId().equals(userId))
              .findFirst()
              .orElseThrow();
      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getUserId()).isEqualTo(userId);
      assertThat(saved.getSkuId()).isEqualTo(skuId);
      assertThat(saved.getQuantity()).isEqualTo(2);
      assertThat(cartRepository.countByUserId(userId)).isEqualTo(1);
      verify(cartProductPort).validateSkuForCart(skuId);
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
      Cart target = cartRepository.save(Cart.create(UUID.randomUUID(), userId, skuId, 2));
      entityManager.flush();
      entityManager.clear();

      // when
      cartService.updateCartItem(new UpdateCartItemCommand(userId, target.getId(), quantity));
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
      Cart first = cartRepository.save(Cart.create(UUID.randomUUID(), userId, skuId, 2));
      Cart second =
          cartRepository.save(Cart.create(UUID.randomUUID(), userId, UUID.randomUUID(), 3));
      Cart unselected =
          cartRepository.save(Cart.create(UUID.randomUUID(), userId, UUID.randomUUID(), 1));
      Cart other = cartRepository.save(Cart.create(UUID.randomUUID(), UUID.randomUUID(), skuId, 4));
      DeleteCartItemCommand command =
          new DeleteCartItemCommand(
              userId, List.of(first.getId(), second.getId(), other.getId(), UUID.randomUUID()));
      entityManager.flush();
      entityManager.clear();

      // when
      cartService.removeCartItem(command);
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
