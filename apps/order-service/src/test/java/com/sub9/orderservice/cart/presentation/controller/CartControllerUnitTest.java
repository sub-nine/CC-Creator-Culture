package com.sub9.orderservice.cart.presentation.controller;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import com.sub9.orderservice.cart.application.service.CartService;
import com.sub9.orderservice.cart.domain.exception.CartErrorCode;
import com.sub9.orderservice.cart.presentation.request.AddCartItemRequest;
import com.sub9.orderservice.support.AbstractControllerTest;
import java.time.Instant;
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
  @MockitoBean private CartService cartService;
  private final UUID userId = UUID.randomUUID();
  private final UUID skuId = UUID.randomUUID();

  @Nested
  @DisplayName("장바구니 등록 API 테스트")
  class AddCartItemTests {
    @Test
    @DisplayName("장바구니 등록에 성공하고 응답을 반환한다.")
    void addCartItem_success() throws Exception {
      // given
      AddCartItemRequest request = new AddCartItemRequest(skuId, 2);

      // when & then
      mockMvc
          .perform(authenticatedRequest().content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("장바구니 등록 성공"));
      verify(cartService).addCartItem(new AddCartItemCommand(userId, skuId, 2));
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
      verifyNoInteractions(cartService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("수량이 0 이하이면 400 예외가 발생해야한다.")
    void addCartItem_fails_when_quantity_not_positive(int quantity) throws Exception {
      // given
      AddCartItemRequest request = new AddCartItemRequest(skuId, quantity);

      // when & then
      mockMvc
          .perform(authenticatedRequest().content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.VALIDATION_ERROR.code()));
      verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("인증 정보가 없으면 401 예외가 발생해야한다..")
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
      verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("중복된 상품을 저장할 경우 400 예외가 발생해야한다.")
    void addCartItem_fails_when_cart_item_already_exists() throws Exception {
      // given
      AddCartItemRequest request = new AddCartItemRequest(skuId, 2);
      willThrow(new BusinessException(CartErrorCode.CART_ITEM_ALREADY_EXISTS))
          .given(cartService)
          .addCartItem(new AddCartItemCommand(userId, skuId, 2));

      // when & then
      mockMvc
          .perform(authenticatedRequest().content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value(CartErrorCode.CART_ITEM_ALREADY_EXISTS.code()));
    }
  }

  private MockHttpServletRequestBuilder authenticatedRequest() {
    return post("/api/v1/cart/items")
        .header("X-User-Id", userId)
        .header("X-User-Role", "CUSTOMER")
        .header("X-Token-Id", UUID.randomUUID())
        .header("X-Token-Expires-At", Instant.now().plusSeconds(3600).getEpochSecond())
        .contentType(MediaType.APPLICATION_JSON);
  }
}
