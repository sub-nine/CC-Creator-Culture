package com.sub9.productservice.product.presentation.query.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sub9.productservice.common.config.r2.R2Properties;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.port.in.product.ProductQueryUseCase;
import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.support.AbstractControllerTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("ProductQueryController - 단위 테스트")
@WebMvcTest(ProductQueryController.class)
class ProductQueryControllerUnitTest extends AbstractControllerTest {
  @MockitoBean ProductQueryUseCase productQueryUseCase;
  @MockitoBean R2Properties r2Properties;

  private final UUID productId = UUID.randomUUID();
  private final String endPoint = "/api/v1/products";

  @Test
  @DisplayName("상품 검색에 성공하면 상품 목록과 200을 반환한다.")
  void searchProducts_success() throws Exception {
    // given
    String keyword = "왁뿌볼";
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
    ProductInfo response =
        new ProductInfo(
            productId,
            UUID.randomUUID(),
            "상호",
            "왁뿌볼",
            ProductStatus.ACTIVE,
            BigDecimal.valueOf(4.5),
            3L,
            10000L,
            10,
            "products/thumbnail.webp");

    given(r2Properties.publicUrl()).willReturn("https://images.example.com");
    given(productQueryUseCase.searchProducts(eq(keyword), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(response), pageable, 1));

    // when & then
    mockMvc
        .perform(get(endPoint).param("keyword", keyword).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("요청 성공"))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].productId").value(productId.toString()))
        .andExpect(jsonPath("$.data.content[0].creatorName").value("상호"))
        .andExpect(jsonPath("$.data.content[0].name").value("왁뿌볼"))
        .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.content[0].averageRating").value(4.5))
        .andExpect(jsonPath("$.data.content[0].reviewCount").value(3))
        .andExpect(jsonPath("$.data.content[0].price").value(10000))
        .andExpect(jsonPath("$.data.content[0].quantity").value(10))
        .andExpect(
            jsonPath("$.data.content[0].imageUrl")
                .value("https://images.example.com/products/thumbnail.webp"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    verify(productQueryUseCase).searchProducts(keyword, pageable);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @DisplayName("대표 이미지가 없으면 이미지 URL 없이 상품 목록과 200을 반환한다.")
  void searchProducts_success_when_image_is_missing(String imageKey) throws Exception {
    // given
    ProductInfo response =
        new ProductInfo(
            productId,
            UUID.randomUUID(),
            null,
            "말랑이",
            ProductStatus.ACTIVE,
            null,
            0L,
            10000L,
            10,
            imageKey);

    given(productQueryUseCase.searchProducts(eq("말랑"), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(response)));

    // when & then
    mockMvc
        .perform(get(endPoint).param("keyword", "말랑").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].productId").value(productId.toString()))
        .andExpect(jsonPath("$.data.content[0].imageUrl").doesNotExist());
  }

  @Test
  @DisplayName("상품 상세 정보 조회에 성공하면 상품 정보와 200을 반환한다.")
  void getProductDetail_success() throws Exception {
    // given
    ProductDetailInfo response =
        new ProductDetailInfo(
            productId,
            UUID.randomUUID(),
            "상호",
            "왁뿌볼",
            "설명",
            ProductStatus.ACTIVE,
            0L,
            BigDecimal.valueOf(4.5),
            0L,
            List.of(new ProductDetailInfo.CategoryInfo(UUID.randomUUID(), "의류")),
            List.of(new ProductDetailInfo.HashtagInfo(UUID.randomUUID(), "여름")),
            List.of(),
            List.of(new ProductDetailInfo.ImageInfo(UUID.randomUUID(), "products/detail.webp", 0)));

    given(r2Properties.publicUrl()).willReturn("https://images.example.com");
    given(productQueryUseCase.getProductDetail(eq(productId), any())).willReturn(response);

    // when & then
    mockMvc
        .perform(get(endPoint + "/{productId}", productId).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("요청 성공"))
        .andExpect(jsonPath("$.data.productId").value(productId.toString()))
        .andExpect(jsonPath("$.data.creatorName").value("상호"))
        .andExpect(jsonPath("$.data.categories[0].name").value("의류"))
        .andExpect(jsonPath("$.data.hashtags[0].name").value("여름"))
        .andExpect(
            jsonPath("$.data.images[0].imageUrl")
                .value("https://images.example.com/products/detail.webp"))
        .andExpect(jsonPath("$.data.images[0].sortOrder").value(0))
        .andExpect(jsonPath("$.data.images[0].imageKey").doesNotExist())
        .andExpect(cookie().value("visitor_cookie", org.hamcrest.Matchers.startsWith("guest:")))
        .andExpect(cookie().httpOnly("visitor_cookie", true))
        .andExpect(cookie().path("visitor_cookie", endPoint))
        .andExpect(cookie().maxAge("visitor_cookie", 30 * 24 * 60 * 60))
        .andExpect(cookie().attribute("visitor_cookie", "SameSite", "Lax"));

    ArgumentCaptor<String> visitorId = ArgumentCaptor.forClass(String.class);
    verify(productQueryUseCase).getProductDetail(eq(productId), visitorId.capture());
    assertThat(visitorId.getValue()).startsWith("guest:");
    assertThat(UUID.fromString(visitorId.getValue().substring("guest:".length()))).isNotNull();
  }

  @Test
  @DisplayName("비회원은 기존 방문자 쿠키가 있을 시 쿠키를 재발급하지 않는다.")
  void getProductDetail_success_when_guest_cookie_exists() throws Exception {
    // given
    String visitorId = "guest:" + UUID.randomUUID();
    given(productQueryUseCase.getProductDetail(productId, visitorId))
        .willReturn(createProductDetailInfo());

    // when & then
    mockMvc
        .perform(
            get(endPoint + "/{productId}", productId)
                .cookie(new Cookie("visitor_cookie", visitorId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.productId").value(productId.toString()))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    verify(productQueryUseCase).getProductDetail(productId, visitorId);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"guest:550e8400-e29b-41d4-a716-446655440000", "invalid"})
  @DisplayName("회원은 userId를 통해 상품 상세 조회 시 조회수를 집계한다..")
  void getProductDetail_success_when_authenticated(String visitorCookie) throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(productQueryUseCase.getProductDetail(productId, "user:" + userId))
        .willReturn(createProductDetailInfo());
    var request =
        get(endPoint + "/{productId}", productId)
            .with(authentication(CustomAuthenticationToken.of(userId, "USER")));
    if (visitorCookie != null) {
      request.cookie(new Cookie("visitor_cookie", visitorCookie));
    }

    // when & then
    mockMvc
        .perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.productId").value(productId.toString()))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    verify(productQueryUseCase).getProductDetail(productId, "user:" + userId);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        " ",
        "invalid",
        "guest:",
        "guest:not-a-uuid",
        "user:550e8400-e29b-41d4-a716-446655440000"
      })
  @DisplayName("비회원 방문자 쿠키가 유효하지 않으면 새 쿠키를 발급하고 상품을 조회한다.")
  void getProductDetail_success_when_guest_cookie_is_invalid(String visitorCookie)
      throws Exception {
    // given
    given(productQueryUseCase.getProductDetail(eq(productId), any()))
        .willReturn(createProductDetailInfo());

    // when
    var response =
        mockMvc
            .perform(
                get(endPoint + "/{productId}", productId)
                    .cookie(new Cookie("visitor_cookie", visitorCookie)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.productId").value(productId.toString()))
            .andReturn()
            .getResponse();

    // then
    Cookie cookie = response.getCookie("visitor_cookie");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).startsWith("guest:").isNotEqualTo(visitorCookie);
    assertThat(UUID.fromString(cookie.getValue().substring("guest:".length()))).isNotNull();
    verify(productQueryUseCase).getProductDetail(productId, cookie.getValue());
  }

  private ProductDetailInfo createProductDetailInfo() {
    return new ProductDetailInfo(
        productId,
        UUID.randomUUID(),
        "상호",
        "말랑이",
        "말랑이 설명",
        ProductStatus.ACTIVE,
        0L,
        null,
        0L,
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
