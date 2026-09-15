package com.sub9.orderservice.cart.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;

import com.sub9.orderservice.support.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.infrastructure.client.exception.CartProductClientErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SpringBootTest
@DisplayName("CartProductFeignAdapter - 통합 테스트")
class CartProductFeignAdapterIntegrationTest extends AbstractIntegrationTest {
  @MockitoBean private CartProductFeignClient feignClient;
  @Autowired private CartProductFeignAdapter adapter;
  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @BeforeEach
  void setUp() {
    circuitBreakerRegistry.circuitBreaker("productService").reset();
  }

  @Nested
  @DisplayName("장바구니 상품 검증 테스트")
  class GetValidatedProductIdForCartTests {

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409})
    @DisplayName("상품 검증에 실패하면 INVALID_CART_PRODUCT 예외가 발생해야한다.")
    void getValidatedProductIdForCart_fails_when_product_invalid(int status) {
      // given
      UUID skuId = UUID.randomUUID();
      willThrow(feignException(status)).given(feignClient).getValidatedProductIdForCart(skuId);

      // when & then
      assertThatThrownBy(() -> adapter.getValidatedProductIdForCart(skuId))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CartProductClientErrorCode.INVALID_CART_PRODUCT.message());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    @DisplayName("상품 서비스 연결에 실패하면 SERVICE_UNAVAILABLE 예외가 발생해야한다..")
    void getValidatedProductIdForCart_fails_when_service_unavailable(int status) {
      // given
      UUID skuId = UUID.randomUUID();
      willThrow(feignException(status)).given(feignClient).getValidatedProductIdForCart(skuId);

      // when & then
      assertThatThrownBy(() -> adapter.getValidatedProductIdForCart(skuId))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CommonErrorCode.SERVICE_UNAVAILABLE.message());
    }
  }

  @Nested
  @DisplayName("장바구니 상품 조회 테스트")
  class GetCartItemProductsTests {

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409})
    @DisplayName("상품 조회 요청이 유효하지 않으면 INVALID_CART_PRODUCT 예외가 발생한다.")
    void getCartItemProducts_fails_when_product_invalid(int status) {
      // given
      List<UUID> skuIds = List.of(UUID.randomUUID());
      willThrow(feignException(status)).given(feignClient).getProductsForCart(skuIds);

      // when & then
      assertThatThrownBy(() -> adapter.getCartItemProducts(skuIds))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CartProductClientErrorCode.INVALID_CART_PRODUCT.message());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    @DisplayName("상품 조회 중 서비스 장애가 발생하면 SERVICE_UNAVAILABLE 예외가 발생한다.")
    void getCartItemProducts_fails_when_service_unavailable(int status) {
      // given
      List<UUID> skuIds = List.of(UUID.randomUUID());
      willThrow(feignException(status)).given(feignClient).getProductsForCart(skuIds);

      // when & then
      assertThatThrownBy(() -> adapter.getCartItemProducts(skuIds))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CommonErrorCode.SERVICE_UNAVAILABLE.message());
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
        "getValidatedProductIdForCart", Response.builder().status(status).request(request).build());
  }
}
