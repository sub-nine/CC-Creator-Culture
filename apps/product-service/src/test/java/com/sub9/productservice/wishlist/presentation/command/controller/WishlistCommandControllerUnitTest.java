package com.sub9.productservice.wishlist.presentation.command.controller;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.support.AbstractControllerTest;
import com.sub9.productservice.wishlist.application.command.dto.AddToWishlistCommand;
import com.sub9.productservice.wishlist.application.command.dto.RemoveFromWishlistCommand;
import com.sub9.productservice.wishlist.application.port.in.WishlistCommandUseCase;
import com.sub9.productservice.wishlist.domain.exception.WishlistErrorCode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(WishlistCommandController.class)
@DisplayName("WishlistCommandController - 단위 테스트")
class WishlistCommandControllerUnitTest extends AbstractControllerTest {
  @MockitoBean WishlistCommandUseCase wishlistCommandUseCase;

  private final UUID userId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final String endPoint = "/api/v1/wishlist/{productId}";

  @Nested
  @DisplayName("관심상품 등록 테스트")
  class AddToWishlistTests {
    @Test
    @DisplayName("관심상품 등록에 성공하면 201과 성공 메시지를 반환한다")
    void addToWishlist_success() throws Exception {
      // when & then
      mockMvc
          .perform(
              post(endPoint, productId)
                  .with(authentication(CustomAuthenticationToken.of(userId, "USER")))
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.message").value("관심상품 등록에 성공했습니다."))
          .andExpect(jsonPath("$.data").doesNotExist());
      verify(wishlistCommandUseCase).addToWishlist(new AddToWishlistCommand(userId, productId));
    }

    @Test
    @DisplayName("등록할 수 없는 상품이면 404와 PRODUCT_NOT_AVAILABLE 예외를 반환한다")
    void addToWishlist_fails_when_product_is_not_available() throws Exception {
      // given
      WishlistErrorCode errorCode = WishlistErrorCode.PRODUCT_NOT_AVAILABLE;
      willThrow(new BusinessException(errorCode))
          .given(wishlistCommandUseCase)
          .addToWishlist(new AddToWishlistCommand(userId, productId));

      // when & then
      mockMvc
          .perform(
              post(endPoint, productId)
                  .with(authentication(CustomAuthenticationToken.of(userId, "CUSTOMAER"))))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.errorCode").value(errorCode.code()));
    }

    @Test
    @DisplayName("상품 ID 형식이 올바르지 않으면 400을 반환한다")
    void addToWishlist_fails_when_product_id_is_invalid() throws Exception {
      // when & then
      mockMvc
          .perform(
              post(endPoint, "id")
                  .with(authentication(CustomAuthenticationToken.of(userId, "CUSTOMAER"))))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(wishlistCommandUseCase);
    }
  }

  @Nested
  @DisplayName("관심상품 삭제 테스트")
  class RemoveTests {
    private final UUID wishlistId = UUID.randomUUID();

    @Test
    @DisplayName("관심상품을 삭제하고 200을 반환한다")
    void removeFromWishlist_success() throws Exception {
      // when & then
      mockMvc
          .perform(
              delete("/api/v1/wishlist")
                  .with(authentication(CustomAuthenticationToken.of(userId, "CUSTOMAER")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(List.of(wishlistId))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("관심상품 삭제에 성공했습니다."))
          .andExpect(jsonPath("$.data").doesNotExist());
      verify(wishlistCommandUseCase)
          .removeFromWishlist(new RemoveFromWishlistCommand(userId, Set.of(wishlistId)));
    }

    @Test
    @DisplayName("여러 관심상품을 삭제하고 200을 반환한다")
    void removeFromWishlist_success_when_multiple_wishlists_are_selected() throws Exception {
      // given
      UUID secondWishlistId = UUID.randomUUID();

      // when & then
      mockMvc
          .perform(
              delete("/api/v1/wishlist")
                  .with(authentication(CustomAuthenticationToken.of(userId, "CUSTOMAER")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(List.of(wishlistId, secondWishlistId))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("관심상품 삭제에 성공했습니다."));
      verify(wishlistCommandUseCase)
          .removeFromWishlist(
              new RemoveFromWishlistCommand(userId, Set.of(wishlistId, secondWishlistId)));
    }

    @Test
    @DisplayName("삭제할 관심상품이 존재하지 않으면 404를 반환한다")
    void removeFromWishlist_fails_when_wishlist_is_not_found() throws Exception {
      // given
      WishlistErrorCode errorCode = WishlistErrorCode.WISHLIST_NOT_FOUND;
      willThrow(new BusinessException(errorCode))
          .given(wishlistCommandUseCase)
          .removeFromWishlist(new RemoveFromWishlistCommand(userId, Set.of(wishlistId)));

      // when & then
      mockMvc
          .perform(
              delete("/api/v1/wishlist")
                  .with(authentication(CustomAuthenticationToken.of(userId, "CUSTOMAER")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(List.of(wishlistId))))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.errorCode").value(errorCode.code()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[\"invalid-id\"]", "[]", "[null]", "null", ""})
    @DisplayName("관심상품 ID가 잘못되거나 본문이 비어 있으면 400을 반환한다")
    void removeFromWishlist_fails_when_body_is_invalid(String body) throws Exception {
      // when & then
      mockMvc
          .perform(
              delete("/api/v1/wishlist")
                  .with(authentication(CustomAuthenticationToken.of(userId, "CUSTOMAER")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(wishlistCommandUseCase);
    }
  }
}
