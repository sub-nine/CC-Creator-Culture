package com.sub9.orderservice.cart.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.application.dto.CartItemInfo;
import com.sub9.orderservice.cart.application.dto.CartProductInfo;
import com.sub9.orderservice.cart.application.port.out.CartProductPort;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.domain.repository.CartRepository;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartQueryService - 단위 테스트")
class CartQueryServiceUnitTest {
  @Mock
  private CartRepository cartRepository;
  @Mock
  private CartProductPort cartProductPort;
  @InjectMocks
  private CartQueryService cartQueryService;

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
  @DisplayName("주문용 장바구니 조회 테스트")
  class GetCartItemsTests {
    @Test
    @DisplayName("선택한 항목 중 조회된 본인 항목의 정보만 반환한다.")
    void getCartItems_success_when_some_items_not_found() {
      // given
      List<UUID> ids = List.of(cart.getId(), UUID.randomUUID());
      CartProductInfo product = new CartProductInfo(
          cart.getSkuId(), UUID.randomUUID(), UUID.randomUUID(), "상품", "옵션", "ACTIVE", 1000L);

      given(cartRepository.findAllByUserIdAndIdIn(userId, ids)).willReturn(List.of(cart));
      given(cartProductPort.getCartItemProducts(List.of(cart.getSkuId())))
          .willReturn(List.of(product));

      // when
      List<CartItemInfo> result = cartQueryService.getCartItems(userId, ids);

      // then
      assertThat(result).containsExactly(new CartItemInfo(
          cart.getId(), cart.getSkuId(), product.productId(), product.creatorId(),
          "상품", "옵션", "ACTIVE", 1000L, 3));
    }

    @Test
    @DisplayName("조회된 항목이 없으면 빈 목록을 반환하고 상품 서비스를 호출하지 않는다.")
    void getCartItems_success_when_empty() {
      // given
      List<UUID> ids = List.of(cart.getId());

      given(cartRepository.findAllByUserIdAndIdIn(userId, ids)).willReturn(List.of());

      // when
      List<CartItemInfo> result = cartQueryService.getCartItems(userId, ids);

      // then
      assertThat(result).isEmpty();
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
