package com.sub9.orderservice.cart.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.sub9.orderservice.cart.application.dto.CartProductInfo;
import com.sub9.orderservice.cart.application.port.CartProductPort;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.infrastructure.persistence.CartJpaRepository;
import com.sub9.orderservice.cart.presentation.response.CartItemResponse;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort.CartItemSnapshot;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockPort;
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
@DisplayName("CartQueryService - 통합 테스트")
class CartQueryServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired private CartQueryService cartQueryService;
  @Autowired private CartJpaRepository cartRepository;
  @Autowired private EntityManager entityManager;

  @MockitoBean private CartProductPort cartProductPort;
  @MockitoBean private CouponApplicationPort couponApplicationPort;
  @MockitoBean private CouponUsagePort couponUsagePort;
  @MockitoBean private StockPort stockPort;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("장바구니 조회 성공 테스트")
  class GetCartTests {
    @Test
    @DisplayName("본인 장바구니를 조회하고 SKU별 상품 정보와 장바구니 수량을 반환한다.")
    void getCart_success() {
      // given
      Cart first = saveCart(userId, 2);
      Cart second = saveCart(userId, 5);

      saveCart(UUID.randomUUID(), 7);

      CartProductInfo firstInfo = productInfo(first.getSkuId(), "첫 상품", 1000L);
      CartProductInfo secondInfo = productInfo(second.getSkuId(), "둘째 상품", 2000L);

      given(cartProductPort.getCartItemProducts(anyList()))
          .willReturn(List.of(secondInfo, firstInfo));

      entityManager.flush();
      entityManager.clear();

      // when
      List<CartItemResponse> result = cartQueryService.getCart(userId);

      // then
      assertThat(result)
          .containsExactlyInAnyOrder(
              new CartItemResponse(
                  first.getId(), first.getSkuId(), "첫 상품", "옵션", "ACTIVE", 2, 1000L),
              new CartItemResponse(
                  second.getId(), second.getSkuId(), "둘째 상품", "옵션", "ACTIVE", 5, 2000L));

      verify(cartProductPort)
          .getCartItemProducts(
              argThat(
                  ids ->
                      ids.size() == 2
                          && ids.containsAll(List.of(first.getSkuId(), second.getSkuId()))));
    }

    @Test
    @DisplayName("장바구니가 비어 있으면 빈 목록을 반환하고 상품 서비스를 호출하지 않는다.")
    void getCart_success_when_empty() {
      // given
      saveCart(UUID.randomUUID(), 2);
      entityManager.flush();
      entityManager.clear();

      // when
      List<CartItemResponse> result = cartQueryService.getCart(userId);

      // then
      assertThat(result).isEmpty();
      verifyNoInteractions(cartProductPort);
    }

    @Test
    @DisplayName("상품 정보가 누락된 항목은 제외하고 나머지 항목을 반환한다.")
    void getCart_success_when_product_missing() {
      // given
      Cart valid = saveCart(userId, 3);
      Cart missing = saveCart(userId, 1);

      given(cartProductPort.getCartItemProducts(anyList()))
          .willReturn(List.of(productInfo(valid.getSkuId(), "상품", 1000L)));

      entityManager.flush();
      entityManager.clear();

      // when
      List<CartItemResponse> result = cartQueryService.getCart(userId);

      // then
      assertThat(result).extracting(CartItemResponse::cartId).containsExactly(valid.getId());
      assertThat(cartRepository.findById(missing.getId())).isPresent();
    }

    @Test
    @DisplayName("모든 상품 정보가 누락되면 빈 목록을 반환한다.")
    void getCart_success_when_all_products_missing() {
      // given
      saveCart(userId, 2);

      given(cartProductPort.getCartItemProducts(anyList())).willReturn(List.of());

      entityManager.flush();
      entityManager.clear();

      // when
      List<CartItemResponse> result = cartQueryService.getCart(userId);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("주문용 장바구니 조회 성공 테스트")
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
          cartQueryService.getCartItems(userId, List.of(selected.getId()));

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
