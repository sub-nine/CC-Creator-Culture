package com.sub9.orderservice.cart.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import com.sub9.orderservice.cart.application.port.CartProductPort;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.infrastructure.persistence.CartJpaRepository;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.*;
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
}
