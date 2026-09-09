package com.sub9.orderservice.cart.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.application.dto.CartItemInfo;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort.CartItemSnapshot;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartSnapshotAdapter - 단위 테스트")
class CartSnapshotAdapterUnitTest {
  @Mock private CartQueryService cartQueryService;
  @InjectMocks private CartSnapshotAdapter adapter;

  private UUID userId;
  private CartItemInfo item;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();

    item = new CartItemInfo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), "상품", "옵션", "ACTIVE", 3000L, 4);
  }

  @Nested
  @DisplayName("주문용 장바구니 스냅샷 조회 테스트")
  class GetCartItemsTests {
    @Test
    @DisplayName("장바구니 정보를 주문 스냅샷으로 변환한다.")
    void getCartItems_success() {
      // given
      List<UUID> ids = List.of(item.cartId());
      given(cartQueryService.getCartItems(userId, ids)).willReturn(List.of(item));

      // when
      List<CartItemSnapshot> result = adapter.getCartItems(userId, ids);

      // then
      assertThat(result).containsExactly(new CartItemSnapshot(
          item.cartId(), item.creatorId(), item.productId(), item.skuId(),
          "상품", "옵션", 3000L, 4));
    }

    @Test
    @DisplayName("선택한 항목 중 조회되지 않은 항목이 있으면 INVALID_ORDER_ITEMS 예외가 발생해야한다.")
    void getCartItems_fails_when_cart_item_not_found() {
      // given
      List<UUID> ids = List.of(item.cartId(), UUID.randomUUID());
      given(cartQueryService.getCartItems(userId, ids)).willReturn(List.of(item));

      // when & then
      assertThatThrownBy(() -> adapter.getCartItems(userId, ids))
          .isInstanceOf(BusinessException.class)
          .hasMessage(OrderErrorCode.INVALID_ORDER_ITEMS.message());
    }

    @Test
    @DisplayName("선택한 항목이 모두 없으면 INVALID_ORDER_ITEMS 예외가 발생해야한다.")
    void getCartItems_fails_when_all_items_not_found() {
      // given
      List<UUID> ids = List.of(item.cartId());
      given(cartQueryService.getCartItems(userId, ids)).willReturn(List.of());

      // when & then
      assertThatThrownBy(() -> adapter.getCartItems(userId, ids))
          .isInstanceOf(BusinessException.class)
          .hasMessage(OrderErrorCode.INVALID_ORDER_ITEMS.message());
    }

    @Test
    @DisplayName("상품 서비스 연결 실패 예외를 그대로 전달한다.")
    void getCartItems_fails_when_service_unavailable() {
      // given
      List<UUID> ids = List.of(item.cartId());
      given(cartQueryService.getCartItems(userId, ids))
          .willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

      // when & then
      assertThatThrownBy(() -> adapter.getCartItems(userId, ids))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CommonErrorCode.SERVICE_UNAVAILABLE.message());
    }
  }
}
