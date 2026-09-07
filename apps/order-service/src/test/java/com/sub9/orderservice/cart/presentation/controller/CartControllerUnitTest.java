package com.sub9.orderservice.cart.presentation.controller;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import com.sub9.orderservice.cart.application.dto.DeleteCartItemCommand;
import com.sub9.orderservice.cart.application.dto.UpdateCartItemCommand;
import com.sub9.orderservice.cart.application.service.CartCommandService;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.cart.domain.exception.CartErrorCode;
import com.sub9.orderservice.cart.presentation.request.AddCartItemRequest;
import com.sub9.orderservice.cart.presentation.request.DeleteCartItemRequest;
import com.sub9.orderservice.cart.presentation.request.UpdateCartItemRequest;
import com.sub9.orderservice.support.AbstractControllerTest;
import com.sub9.orderservice.cart.presentation.response.CartItemResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@DisplayName("CartController - 단위 테스트")
@WebMvcTest(controllers = CartController.class)
class CartControllerUnitTest extends AbstractControllerTest {
  @MockitoBean private CartCommandService cartCommandService;
  @MockitoBean private CartQueryService cartQueryService;

  private final UUID userId = UUID.randomUUID();
  private final UUID skuId = UUID.randomUUID();

  @Nested
  @DisplayName("장바구니 등록 API 테스트")
  class AddCartItemTests {
    @Test
    @DisplayName("장바구니 등록에 성공하면 성공 응답을 반환한다.")
    void addCartItem_success() throws Exception {
      // given
      AddCartItemRequest request = new AddCartItemRequest(skuId, 2);

      // when & then
      mockMvc
          .perform(authenticatedRequest().content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("장바구니 등록 성공"));
      verify(cartCommandService).addCartItem(new AddCartItemCommand(userId, skuId, 2));
    }

    @Test
    @DisplayName("SKU가 존재하지 않으면 400 예외가 발생해야한다.")
    void addCartItem_fails_when_sku_id_missing() throws Exception {
      // given
      AddCartItemRequest request = new AddCartItemRequest(null, 2);

      // when & then
      mockMvc
          .perform(authenticatedRequest().content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.VALIDATION_ERROR.code()));
      verifyNoInteractions(cartCommandService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 100})
    @DisplayName("수량이 1~99 범위를 벗어나면 400 예외가 발생해야한다.")
    void addCartItem_fails_when_quantity_out_of_range(int quantity) throws Exception {
      // given
      AddCartItemRequest request = new AddCartItemRequest(skuId, quantity);

      // when & then
      mockMvc
          .perform(authenticatedRequest().content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.VALIDATION_ERROR.code()));
      verifyNoInteractions(cartCommandService);
    }

    @Test
    @DisplayName("인증 정보가 없으면 401 예외가 발생해야한다.")
    void addCartItem_fails_when_unauthenticated() throws Exception {
      // given
      AddCartItemRequest request = new AddCartItemRequest(skuId, 2);

      // when & then
      mockMvc
          .perform(
              post("/api/v1/cart/items")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isUnauthorized());
      verifyNoInteractions(cartCommandService);
    }

    @Test
    @DisplayName("중복된 상품을 저장할 경우 400 예외가 발생해야한다.")
    void addCartItem_fails_when_cart_item_already_exists() throws Exception {
      // given
      AddCartItemRequest request = new AddCartItemRequest(skuId, 2);
      willThrow(new BusinessException(CartErrorCode.CART_ITEM_ALREADY_EXISTS))
          .given(cartCommandService)
          .addCartItem(new AddCartItemCommand(userId, skuId, 2));

      // when & then
      mockMvc
          .perform(authenticatedRequest().content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value(CartErrorCode.CART_ITEM_ALREADY_EXISTS.code()));
    }
  }

  @Nested
  @DisplayName("장바구니 수정 API 테스트")
  class UpdateCartItemTests {
    @Test
    @DisplayName("장바구니 수정에 성공하면 성공 응답을 반환한다.")
    void updateCartItem_success() throws Exception {
      // given
      UUID cartId = UUID.randomUUID();
      UpdateCartItemRequest request = new UpdateCartItemRequest(3);

      // when & then
      mockMvc
          .perform(
              authenticatedRequest(patch("/api/v1/cart/items/{cartId}", cartId))
                  .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("상품 수량이 변경되었습니다."));
      verify(cartCommandService).updateCartItem(new UpdateCartItemCommand(userId, cartId, 3));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 100})
    @DisplayName("수량이 1~99 범위를 벗어나면 400 예외가 발생해야한다.")
    void updateCartItem_fails_when_quantity_out_of_range(int quantity) throws Exception {
      // given
      UpdateCartItemRequest request = new UpdateCartItemRequest(quantity);

      // when & then
      mockMvc
          .perform(
              authenticatedRequest(patch("/api/v1/cart/items/{cartId}", UUID.randomUUID()))
                  .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.VALIDATION_ERROR.code()));
      verifyNoInteractions(cartCommandService);
    }

    @Test
    @DisplayName("본인 소유의 항목이 없으면 404 예외가 발생해야한다.")
    void updateCartItem_fails_when_cart_item_not_found() throws Exception {
      // given
      UUID cartId = UUID.randomUUID();
      willThrow(new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND))
          .given(cartCommandService)
          .updateCartItem(new UpdateCartItemCommand(userId, cartId, 3));

      // when & then
      mockMvc
          .perform(
              authenticatedRequest(patch("/api/v1/cart/items/{cartId}", cartId))
                  .content(jsonMapper.writeValueAsString(new UpdateCartItemRequest(3))))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.errorCode").value(CartErrorCode.CART_ITEM_NOT_FOUND.code()));
    }
  }

  @Nested
  @DisplayName("장바구니 삭제 API 테스트")
  class RemoveCartItemTests {
    @Test
    @DisplayName("장바구니 삭제에 성공하면 성공 응답을 반환한다.")
    void removeCartItem_success() throws Exception {
      // given
      List<UUID> cartIds = List.of(UUID.randomUUID(), UUID.randomUUID());
      DeleteCartItemRequest request = new DeleteCartItemRequest(cartIds);

      // when & then
      mockMvc
          .perform(
              authenticatedRequest(post("/api/v1/cart/items/delete"))
                  .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("상품이 장바구니에서 삭제되었습니다"));
      verify(cartCommandService).removeCartItem(new DeleteCartItemCommand(userId, cartIds));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"cartIds\":null}", "{\"cartIds\":[]}", "{\"cartIds\":[null]}"})
    @DisplayName("유효성 체크에 실패하면 400 응답을 반환한다.")
    void removeCartItem_fails_when_cart_ids_invalid(String content) throws Exception {
      // when & then
      mockMvc
          .perform(authenticatedRequest(post("/api/v1/cart/items/delete")).content(content))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.VALIDATION_ERROR.code()));
      verifyNoInteractions(cartCommandService);
    }
  }

  @Nested
  @DisplayName("장바구니 조회 API 테스트")
  class GetCartItemsTests {
    @Test
    @DisplayName("장바구니 조회에 성공하면 상품 정보와 수량을 반환한다.")
    void getCartItems_success() throws Exception {
      // given
      UUID cartId = UUID.randomUUID();
      given(cartQueryService.getCart(userId)).willReturn(List.of(
          new CartItemResponse(cartId, skuId, "상품", "옵션", "ACTIVE", 3, 1000)));

      // when & then
      mockMvc.perform(authenticatedRequest(get("/api/v1/cart/items")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("장바구니 조회 성공"))
          .andExpect(jsonPath("$.data.length()").value(1))
          .andExpect(jsonPath("$.data[0].cartId").value(cartId.toString()))
          .andExpect(jsonPath("$.data[0].skuId").value(skuId.toString()))
          .andExpect(jsonPath("$.data[0].productName").value("상품"))
          .andExpect(jsonPath("$.data[0].skuName").value("옵션"))
          .andExpect(jsonPath("$.data[0].productStatus").value("ACTIVE"))
          .andExpect(jsonPath("$.data[0].quantity").value(3))
          .andExpect(jsonPath("$.data[0].price").value(1000));
      verify(cartQueryService).getCart(userId);
    }

    @Test
    @DisplayName("장바구니가 비어 있으면 빈 목록을 반환한다.")
    void getCartItems_success_when_empty() throws Exception {
      // given
      given(cartQueryService.getCart(userId)).willReturn(List.of());

      // when & then
      mockMvc.perform(authenticatedRequest(get("/api/v1/cart/items")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").isArray())
          .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("인증 정보가 없으면 401 예외가 발생해야한다.")
    void getCartItems_fails_when_unauthenticated() throws Exception {
      // when & then
      mockMvc.perform(get("/api/v1/cart/items"))
          .andExpect(status().isUnauthorized());
      verifyNoInteractions(cartQueryService);
    }

    @Test
    @DisplayName("상품 서비스 연결에 실패하면 503과 SERVICE_UNAVAILABLE 오류 코드를 반환한다.")
    void getCartItems_fails_when_service_unavailable() throws Exception {
      // given
      given(cartQueryService.getCart(userId))
          .willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

      // when & then
      mockMvc.perform(authenticatedRequest(get("/api/v1/cart/items")))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.SERVICE_UNAVAILABLE.code()));
    }
  }

  private MockHttpServletRequestBuilder authenticatedRequest() {
    return authenticatedRequest(post("/api/v1/cart/items"));
  }

  private MockHttpServletRequestBuilder authenticatedRequest(
      MockHttpServletRequestBuilder request) {
    return request
        .header("X-User-Id", userId)
        .header("X-User-Role", "CUSTOMER")
        .header("X-Token-Id", UUID.randomUUID())
        .header("X-Token-Expires-At", Instant.now().plusSeconds(3600).getEpochSecond())
        .contentType(MediaType.APPLICATION_JSON);
  }
}
