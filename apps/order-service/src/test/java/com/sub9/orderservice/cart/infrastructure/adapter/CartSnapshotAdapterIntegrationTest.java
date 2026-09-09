package com.sub9.orderservice.cart.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.cart.application.dto.CartProductInfo;
import com.sub9.orderservice.cart.application.port.out.CartProductPort;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.infrastructure.persistence.CartJpaRepository;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort.CartItemSnapshot;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("CartSnapshotAdapter - 통합 테스트")
class CartSnapshotAdapterIntegrationTest extends AbstractIntegrationTest {
  @Autowired private CartSnapshotPort cartSnapshotPort;
  @Autowired private CartJpaRepository cartRepository;
  @Autowired private EntityManager entityManager;

  @MockitoBean private CartProductPort cartProductPort;
  @MockitoBean private CouponApplicationPort couponApplicationPort;
  @MockitoBean private CouponUsagePort couponUsagePort;
  @MockitoBean private StockPort stockPort;
  @MockitoBean private PaymentCancellationPort paymentCancellationPort;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("주문용 장바구니 조회 테스트")
  class GetCartItemsTests {
    @Test
    @DisplayName("선택한 본인 항목의 상품 정보와 수량으로 주문 스냅샷을 반환한다.")
    void getCartItems_success() {
      // given
      Cart selected = saveCart(userId, 4);

      saveCart(userId, 2);
      saveCart(UUID.randomUUID(), 1);

      CartProductInfo info = productInfo(selected.getSkuId(), "상품", 3000L);

      given(cartProductPort.getCartItemProducts(List.of(selected.getSkuId())))
          .willReturn(List.of(info));

      entityManager.flush();
      entityManager.clear();

      // when
      List<CartItemSnapshot> result =
          cartSnapshotPort.getCartItems(userId, List.of(selected.getId()));

      // then
      assertThat(result)
          .containsExactly(
              new CartItemSnapshot(
                  selected.getId(),
                  info.creatorId(),
                  info.productId(),
                  selected.getSkuId(),
                  info.productName(),
                  info.skuName(),
                  3000L,
                  4));
    }

    @Test
    @DisplayName("타인 항목이 선택되어 있으면 INVALID_ORDER_ITEMS 예외가 발생해야한다.")
    void getCartItems_fails_when_other_user_item_selected() {
      // given
      Cart selected = saveCart(userId, 2);
      Cart other = saveCart(UUID.randomUUID(), 3);

      given(cartProductPort.getCartItemProducts(List.of(selected.getSkuId())))
          .willReturn(List.of(productInfo(selected.getSkuId(), "상품", 1000L)));

      entityManager.flush();
      entityManager.clear();

      // when & then
      assertThatThrownBy(() -> cartSnapshotPort.getCartItems(
          userId, List.of(selected.getId(), other.getId())))
          .isInstanceOf(BusinessException.class)
          .hasMessage(OrderErrorCode.INVALID_ORDER_ITEMS.message());
    }

    @Test
    @DisplayName("선택한 항목의 상품 정보가 누락되면 INVALID_ORDER_ITEMS 예외가 발생해야한다.")
    void getCartItems_fails_when_product_missing() {
      // given
      Cart selected = saveCart(userId, 2);

      given(cartProductPort.getCartItemProducts(List.of(selected.getSkuId())))
          .willReturn(List.of());

      entityManager.flush();
      entityManager.clear();

      // when & then
      assertThatThrownBy(() -> cartSnapshotPort.getCartItems(userId, List.of(selected.getId())))
          .isInstanceOf(BusinessException.class)
          .hasMessage(OrderErrorCode.INVALID_ORDER_ITEMS.message());
    }
  }

  private Cart saveCart(UUID ownerId, int quantity) {
    return cartRepository.save(
        Cart.create(UUID.randomUUID(), ownerId, UUID.randomUUID(), quantity));
  }

  private CartProductInfo productInfo(UUID skuId, String name, long price) {
    return new CartProductInfo(
        skuId, UUID.randomUUID(), UUID.randomUUID(), name, "옵션", "ACTIVE", price);
  }
}
