package com.sub9.productservice.wishlist.presentation.query.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sub9.productservice.common.config.r2.R2Properties;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.support.AbstractControllerTest;
import com.sub9.productservice.wishlist.application.port.in.WishlistQueryUseCase;
import com.sub9.productservice.wishlist.application.query.dto.WishlistInfo;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(WishlistQueryController.class)
@DisplayName("WishlistQueryController - 단위 테스트")
class WishlistQueryControllerUnitTest extends AbstractControllerTest {
  @MockitoBean WishlistQueryUseCase wishlistQueryUseCase;
  @MockitoBean R2Properties r2Properties;

  private final UUID userId = UUID.randomUUID();
  private final AuthUser authUser = new AuthUser(userId, "CUSTOMER");
  private final UUID productId = UUID.randomUUID();
  private final UUID wishlistId = UUID.randomUUID();
  private final String endPoint = "/api/v1/wishlist";

  private RequestPostProcessor authUser(AuthUser authUser) {
    return authentication(CustomAuthenticationToken.of(authUser.id(), authUser.role()));
  }

  @Test
  @DisplayName("페이지 번호를 생략하면 0번째 관심상품 목록과 이미지 URL을 반환한다")
  void getWishlist_success() throws Exception {
    // given
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
    WishlistInfo info =
        new WishlistInfo(wishlistId, productId, "말랑이", "ACTIVE", 10000L, "products/main.webp");
    given(wishlistQueryUseCase.getWishlist(userId, pageable))
        .willReturn(new SliceImpl<>(List.of(info), pageable, true));
    given(r2Properties.publicUrl()).willReturn("https://images.example.com");

    // when & then
    mockMvc
        .perform(get(endPoint).with(authUser(authUser)).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("관심상품 목록 조회 성공"))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].wishlistId").value(wishlistId.toString()))
        .andExpect(jsonPath("$.data.content[0].productId").value(productId.toString()))
        .andExpect(jsonPath("$.data.content[0].productName").value("말랑이"))
        .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.content[0].price").value(10000))
        .andExpect(jsonPath("$.data.content[0].creatorId").doesNotExist())
        .andExpect(
            jsonPath("$.data.content[0].imageUrl")
                .value("https://images.example.com/products/main.webp"))
        .andExpect(jsonPath("$.data.pageNumber").value(0))
        .andExpect(jsonPath("$.data.pageSize").value(10))
        .andExpect(jsonPath("$.data.hasNext").value(true))
        .andExpect(jsonPath("$.data.totalElements").doesNotExist())
        .andExpect(jsonPath("$.data.totalPages").doesNotExist());
    verify(wishlistQueryUseCase).getWishlist(userId, pageable);
  }

  @Test
  @DisplayName("요청한 페이지 번호를 그대로 전달하고 빈 마지막 페이지를 반환한다")
  void getWishlist_success_when_page_is_empty() throws Exception {
    // given
    Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
    given(wishlistQueryUseCase.getWishlist(userId, pageable))
        .willReturn(new SliceImpl<>(List.of(), pageable, false));

    // when & then
    mockMvc
        .perform(get(endPoint).param("pageNum", "1").with(authUser(authUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty())
        .andExpect(jsonPath("$.data.pageNumber").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(false));
    verify(wishlistQueryUseCase).getWishlist(userId, pageable);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @DisplayName("이미지 키가 없으면 이미지 URL 없이 관심상품을 반환한다")
  void getWishlist_success_when_image_is_missing(String imageKey) throws Exception {
    // given
    WishlistInfo info = new WishlistInfo(wishlistId, productId, "말랑이", "ACTIVE", 10000L, imageKey);
    given(wishlistQueryUseCase.getWishlist(eq(userId), any(Pageable.class)))
        .willReturn(new SliceImpl<>(List.of(info)));

    // when & then
    mockMvc
        .perform(get(endPoint).with(authUser(authUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].imageUrl").doesNotExist());
  }

  @Test
  @DisplayName("페이지 번호가 음수이면 400을 반환한다.")
  void getWishlist_fails_when_page_number_is_negative() throws Exception {
    // when & then
    mockMvc
        .perform(get(endPoint).param("pageNum", "-1").with(authUser(authUser)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(wishlistQueryUseCase);
  }
}
