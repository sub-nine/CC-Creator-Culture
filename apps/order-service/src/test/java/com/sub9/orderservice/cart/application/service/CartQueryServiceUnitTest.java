package com.sub9.orderservice.cart.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.application.port.CartProductPort;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.domain.repository.CartRepository;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartQueryService - 단위 테스트")
class CartQueryServiceUnitTest {
  @Mock private CartRepository cartRepository;
  @Mock private CartProductPort cartProductPort;
  @InjectMocks private CartQueryService cartQueryService;

  private UUID userId;
  private Cart cart;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    cart = Cart.create(UUID.randomUUID(), userId, UUID.randomUUID(), 3);
  }

  @Nested
  @DisplayName("장바구니 조회 실패 테스트")
  class GetCartTests {
    @Test
    @DisplayName("상품 서비스 연결에 실패하면 SERVICE_UNAVAILABLE 예외가 발생해야한다.")
    void getCart_fails_when_service_unavailable() {
      // given
      given(cartRepository.findAllByUserId(userId)).willReturn(List.of(cart));
      given(cartProductPort.getCartItemProducts(List.of(cart.getSkuId())))
          .willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

      // when & then
      assertThatThrownBy(() -> cartQueryService.getCart(userId))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CommonErrorCode.SERVICE_UNAVAILABLE.message());
    }
  }

  @Nested
  @DisplayName("주문용 장바구니 조회 실패 테스트")
  class GetCartItemsTests {
    @Test
    @DisplayName("선택한 항목 중 본인 소유가 아니거나 없는 항목이 있으면 INVALID_ORDER_ITEMS 예외가 발생해야한다.")
    void getCartItems_fails_when_cart_item_not_found() {
      // given
      List<UUID> ids = List.of(cart.getId(), UUID.randomUUID());
      given(cartRepository.findAllByUserIdAndIdIn(userId, ids)).willReturn(List.of(cart));

      // when & then
      assertThatThrownBy(() -> cartQueryService.getCartItems(userId, ids))
          .isInstanceOf(BusinessException.class)
          .hasMessage(OrderErrorCode.INVALID_ORDER_ITEMS.message());
      verifyNoInteractions(cartProductPort);
    }

    @Test
    @DisplayName("선택한 SKU의 상품 정보가 누락되면 INVALID_ORDER_ITEMS 예외가 발생해야한다.")
    void getCartItems_fails_when_product_missing() {
      // given
      List<UUID> ids = List.of(cart.getId());
      given(cartRepository.findAllByUserIdAndIdIn(userId, ids)).willReturn(List.of(cart));
      given(cartProductPort.getCartItemProducts(List.of(cart.getSkuId()))).willReturn(List.of());

      // when & then
      assertThatThrownBy(() -> cartQueryService.getCartItems(userId, ids))
          .isInstanceOf(BusinessException.class)
          .hasMessage(OrderErrorCode.INVALID_ORDER_ITEMS.message());
    }
  }
}
