package com.sub9.orderservice.cart.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.application.dto.CartProductInfo;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartProductFeignAdapter - 단위 테스트")
class CartProductFeignAdapterUnitTest {
  @Mock private CartProductFeignClient feignClient;
  @InjectMocks private CartProductFeignAdapter adapter;

  @Nested
  @DisplayName("장바구니 상품 검증 테스트")
  class GetValidatedProductIdForCartTests {
    @Test
    @DisplayName("상품 검증 성공.")
    void getValidatedProductIdForCart_success() {
      // given
      UUID skuId = UUID.randomUUID();

      // when & then
      UUID productId = UUID.randomUUID();
      given(feignClient.getValidatedProductIdForCart(skuId)).willReturn(productId);
      assertThat(adapter.getValidatedProductIdForCart(skuId)).isEqualTo(productId);
      verify(feignClient).getValidatedProductIdForCart(skuId);
    }

    @Test
    @DisplayName("상품 검증 시 응답이 없으면 SERVICE_UNAVAILABLE 예외가 발생한다.")
    void getValidatedProductIdForCart_fails_when_response_is_empty() {
      assertThatThrownBy(() -> adapter.getValidatedProductIdForCart(UUID.randomUUID()))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CommonErrorCode.SERVICE_UNAVAILABLE.message());
    }
  }

  @Nested
  @DisplayName("장바구니 상품 조회 테스트")
  class GetCartItemProductsTests {
    @Test
    @DisplayName("상품 조회에 성공하면 상품 정보 목록을 반환한다.")
    void getCartItemProducts_success() {
      // given
      List<UUID> skuIds = List.of(UUID.randomUUID());

      CartProductInfo info =
          new CartProductInfo(
              skuIds.getFirst(), UUID.randomUUID(), UUID.randomUUID(), "상품", "옵션", "ACTIVE", 1000L);

      given(feignClient.getProductsForCart(skuIds)).willReturn(List.of(info));

      // when
      List<CartProductInfo> result = adapter.getCartItemProducts(skuIds);

      // then
      assertThat(result).containsExactly(info);
      verify(feignClient).getProductsForCart(skuIds);
    }

  }

}
