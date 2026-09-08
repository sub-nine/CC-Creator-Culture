package com.sub9.orderservice.cart.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.application.dto.CartProductInfo;
import com.sub9.orderservice.cart.infrastructure.client.exception.CartProductClientErrorCode;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  class ValidateSkuForCartTests {
    @Test
    @DisplayName("상품 검증 성공.")
    void validateSkuForCart_success() {
      // given
      UUID skuId = UUID.randomUUID();

      // when & then
      assertThatCode(() -> adapter.validateSkuForCart(skuId)).doesNotThrowAnyException();
      verify(feignClient).validateSkuForCart(skuId);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409})
    @DisplayName("상품 검증에 실패하면 INVALID_CART_PRODUCT 예외가 발생해야한다.")
    void validateSkuForCart_fails_when_product_invalid(int status) {
      // given
      UUID skuId = UUID.randomUUID();
      willThrow(feignException(status)).given(feignClient).validateSkuForCart(skuId);

      // when & then
      assertThatThrownBy(() -> adapter.validateSkuForCart(skuId))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CartProductClientErrorCode.INVALID_CART_PRODUCT.message());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    @DisplayName("상품 서비스 연결에 실패하면 SERVICE_UNAVAILABLE 예외가 발생해야한다..")
    void validateSkuForCart_fails_when_service_unavailable(int status) {
      // given
      UUID skuId = UUID.randomUUID();
      willThrow(feignException(status)).given(feignClient).validateSkuForCart(skuId);

      // when & then
      assertThatThrownBy(() -> adapter.validateSkuForCart(skuId))
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

  private FeignException feignException(int status) {
    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "http://product-service/test",
            Map.of(),
            (byte[]) null,
            StandardCharsets.UTF_8,
            null);

    return FeignException.errorStatus(
        "validateSkuForCart", Response.builder().status(status).request(request).build());
  }
}
